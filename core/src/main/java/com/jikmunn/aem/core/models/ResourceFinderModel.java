package com.jikmunn.aem.core.models;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;
import com.day.cq.wcm.api.Page;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Via;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the "Resource Finder" component: a configurable, Query Builder-driven
 * list of resources (e.g. community services, articles, listings) under a
 * given path, optionally filtered by category tag.
 *
 * Dialog fields (author-configured, read via @ValueMapValue):
 *   - searchPath   : root path to search under
 *   - categoryTag  : optional cq:tag id to filter by
 *   - resultLimit  : max results to show (author-configurable, see BUGS_FIXED.md
 *                    for why this has a hard ceiling)
 */
@Model(
        adaptables = SlingHttpServletRequest.class,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class ResourceFinderModel {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceFinderModel.class);

    /** Hard ceiling on p.limit regardless of author input. See BUGS_FIXED.md #1. */
    private static final int MAX_RESULT_LIMIT = 50;
    private static final int DEFAULT_RESULT_LIMIT = 10;

    @ScriptVariable
    private Resource resource;

    @Inject
    private ResourceResolver resourceResolver;

    @Inject
    private QueryBuilder queryBuilder;

    @ValueMapValue
    private String searchPath;

    @ValueMapValue
    private String categoryTag;

    @ValueMapValue
    private Integer resultLimit;

    private List<ResourceItem> items = new ArrayList<>();
    private boolean hasError = false;

    @PostConstruct
    protected void init() {
        if (searchPath == null || searchPath.trim().isEmpty()) {
            // Fail safely and log, never let a misconfigured component throw
            // and break the whole page render.
            LOG.warn("ResourceFinderModel: no searchPath configured on resource {}",
                    resource != null ? resource.getPath() : "unknown");
            hasError = true;
            return;
        }

        try {
            items = fetchResources();
        } catch (RuntimeException e) {
            // Query Builder / repository errors should degrade gracefully on
            // the page, not 500 the whole request. Log with full context so
            // it's actually debuggable from the error log.
            LOG.error("ResourceFinderModel: query failed for path={} tag={}",
                    searchPath, categoryTag, e);
            hasError = true;
        }
    }

    private List<ResourceItem> fetchResources() {
        Map<String, String> predicateMap = new HashMap<>();
        predicateMap.put("path", searchPath);
        predicateMap.put("type", "cq:Page");

        if (categoryTag != null && !categoryTag.trim().isEmpty()) {
            predicateMap.put("tagid", categoryTag);
        }

        // Sort newest-first so authors get predictable ordering without
        // relying on JCR node order (which is not guaranteed stable).
        predicateMap.put("orderby", "@jcr:content/cq:lastModified");
        predicateMap.put("orderby.sort", "desc");

        // Clamp to MAX_RESULT_LIMIT. Never trust author/dialog input directly
        // as a query limit — see BUGS_FIXED.md #1 for the incident this
        // guards against.
        int limit = (resultLimit != null && resultLimit > 0)
                ? Math.min(resultLimit, MAX_RESULT_LIMIT)
                : DEFAULT_RESULT_LIMIT;
        predicateMap.put("p.limit", String.valueOf(limit));

        PredicateGroup predicateGroup = PredicateGroup.create(predicateMap);
        Session session = resourceResolver.adaptTo(Session.class);
        Query query = queryBuilder.createQuery(predicateGroup, session);
        SearchResult result = query.getResult();

        List<ResourceItem> results = new ArrayList<>();
        for (Hit hit : result.getHits()) {
            try {
                Resource hitResource = hit.getResource();
                Page page = hitResource.adaptTo(Page.class);
                if (page != null) {
                    results.add(new ResourceItem(
                            page.getTitle(),
                            page.getPath(),
                            page.getDescription()
                    ));
                }
            } catch (Exception e) {
                // One bad hit should not fail the entire result set — skip
                // and log, keep rendering what we can.
                LOG.warn("ResourceFinderModel: skipped unreadable hit", e);
            }
        }
        return results;
    }

    public List<ResourceItem> getItems() {
        return items;
    }

    public boolean isHasError() {
        return hasError;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Simple read-only view model exposed to the HTL template. */
    public static class ResourceItem {
        private final String title;
        private final String path;
        private final String description;

        public ResourceItem(String title, String path, String description) {
            this.title = title;
            this.path = path;
            this.description = description;
        }

        public String getTitle() {
            return title;
        }

        public String getPath() {
            return path;
        }

        public String getDescription() {
            return description;
        }
    }
}
