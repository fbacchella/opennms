/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.topology.app.internal.ui;


import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.opennms.features.topology.api.GraphContainer;
import org.opennms.features.topology.api.MapViewManager;
import org.opennms.features.topology.api.OperationContext;
import org.opennms.features.topology.api.SelectionContext;
import org.opennms.features.topology.api.SelectionListener;
import org.opennms.features.topology.api.SelectionManager;
import org.opennms.features.topology.api.support.VertexHopGraphProvider;
import org.opennms.features.topology.api.support.VertexHopGraphProvider.FocusNodeHopCriteria;
import org.opennms.features.topology.api.support.VertexHopGraphProvider.VertexHopCriteria;
import org.opennms.features.topology.api.topo.AbstractSearchQuery;
import org.opennms.features.topology.api.topo.AbstractVertexRef;
import org.opennms.features.topology.api.topo.Criteria;
import org.opennms.features.topology.api.topo.SearchProvider;
import org.opennms.features.topology.api.topo.SearchQuery;
import org.opennms.features.topology.api.topo.SearchResult;
import org.opennms.features.topology.api.topo.VertexRef;
import org.opennms.features.topology.app.internal.gwt.client.SearchBoxServerRpc;
import org.opennms.features.topology.app.internal.gwt.client.SearchBoxState;
import org.opennms.features.topology.app.internal.gwt.client.SearchSuggestion;
import org.opennms.osgi.OnmsServiceManager;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.vaadin.ui.AbstractComponent;


public class SearchBox extends AbstractComponent implements SelectionListener, GraphContainer.ChangeListener {

    Multimap<SearchProvider, SearchResult> m_suggestionMap;
    private OperationContext m_operationContext;
    private OnmsServiceManager m_serviceManager;
    SearchBoxServerRpc m_rpc = new SearchBoxServerRpc(){

        private static final long serialVersionUID = 6945103738578953390L;

        @Override
        public void querySuggestions(String query, int indexFrom, int indexTo) {
            if (m_serviceManager != null) {
                getState().setSuggestions(getQueryResults(query));
            }
        }

        @Override
        public void selectSuggestion(SearchSuggestion searchSuggestion) {
            SearchResult searchResult = new SearchResult(searchSuggestion.getId(), searchSuggestion.getNamespace(), searchSuggestion.getLabel());

            Multiset<SearchProvider> keys = m_suggestionMap.keys();
            for(SearchProvider key : keys){
                if(m_suggestionMap.get(key).contains(searchResult)){
                    //key.onFocusSearchResult(searchResult, m_operationContext);

                    break;
                }
            }
        }

        @Override
        public void removeSelected(SearchSuggestion searchSuggestion) {
            SearchResult searchResult = new SearchResult(searchSuggestion.getId(), searchSuggestion.getNamespace(), searchSuggestion.getLabel());

            Multiset<SearchProvider> keys = m_suggestionMap.keys();
            for(SearchProvider key : keys){
                if(m_suggestionMap.get(key).contains(searchResult)){
                    break;
                }
            }

            SelectionManager selectionManager = m_operationContext.getGraphContainer().getSelectionManager();
            selectionManager.deselectVertexRefs(Lists.newArrayList(mapToVertexRef(searchSuggestion)));
        }

        @Override
        public void addToFocus(SearchSuggestion searchSuggestion) {
            SearchResult searchResult = new SearchResult(searchSuggestion.getId(), searchSuggestion.getNamespace(), searchSuggestion.getLabel());

            Multiset<SearchProvider> keys = m_suggestionMap.keys();
            for(SearchProvider key : keys){
                Collection<SearchResult> searchResults = m_suggestionMap.get(key);
                if(searchResults.contains(searchResult)){
                    key.onFocusSearchResult(searchResult, m_operationContext);
                    key.addVertexHopCriteria(searchResult, m_operationContext.getGraphContainer());
                    break;
                }
            }

            m_operationContext.getGraphContainer().redoLayout();
        }

        @Override
        public void removeFocused(SearchSuggestion searchSuggestion) {
            SearchResult searchResult = new SearchResult(searchSuggestion.getId(), searchSuggestion.getNamespace(), searchSuggestion.getLabel());

            Multiset<SearchProvider> keys = m_suggestionMap.keys();
            for(SearchProvider key : keys){
                Collection<SearchResult> searchResults = m_suggestionMap.get(key);
                if(searchResults.contains(searchResult)) {
                    key.onDefocusSearchResult(searchResult, m_operationContext);
                    key.removeVertexHopCriteria(searchResult, m_operationContext.getGraphContainer());

                    break;
                }
            }

            removeIfSpecialURLCase(searchResult);
            m_operationContext.getGraphContainer().redoLayout();
        }

        @Override
        public void centerSearchSuggestion(SearchSuggestion searchSuggestion){
            SearchResult searchResult = new SearchResult(searchSuggestion.getId(), searchSuggestion.getNamespace(), searchSuggestion.getLabel());

            List<VertexRef> vRefs = null;
            Multiset<SearchProvider> keys = m_suggestionMap.keys();
            for(SearchProvider key : keys){
                Collection<SearchResult> searchResults = m_suggestionMap.get(key);
                if(searchResults.contains(searchResult)) {
                    key.onCenterSearchResult(searchResult, m_operationContext.getGraphContainer());
                    vRefs = key.getVertexRefsBy(searchResult);
                    break;
                }
            }

            //Hack for now, change to a better way.
            FocusNodeHopCriteria criteria = VertexHopGraphProvider.getFocusNodeHopCriteriaForContainer(m_operationContext.getGraphContainer());
            AbstractVertexRef vertexRef = new AbstractVertexRef(searchResult.getNamespace(), searchResult.getId(), searchResult.getLabel());
            if(criteria.getVertices().contains(vertexRef)){
                if(vRefs == null){
                    vRefs = Lists.newArrayList();
                }
                vRefs.add(vertexRef);
            }

            GraphContainer graphContainer = m_operationContext.getGraphContainer();
            MapViewManager mapViewManager = graphContainer.getMapViewManager();
            mapViewManager.setBoundingBox(graphContainer.getGraph().getLayout().computeBoundingBox(vRefs));
        }

    };

    public SearchBox(OnmsServiceManager serviceManager, OperationContext operationContext) {
        m_serviceManager = serviceManager;
        m_operationContext = operationContext;
        init();
    }

    public void removeIfSpecialURLCase(SearchResult searchResult) {
        FocusNodeHopCriteria criteria = VertexHopGraphProvider.getFocusNodeHopCriteriaForContainer(m_operationContext.getGraphContainer());
        AbstractVertexRef vertexRef = new AbstractVertexRef(searchResult.getNamespace(), searchResult.getId(), searchResult.getLabel());
        if(criteria.contains(vertexRef)){
            criteria.remove(vertexRef);
        }
    }


    private List<SearchSuggestion> getQueryResults(final String query) {
        String searchPrefix = getQueryPrefix(query);
        String namespace = m_operationContext.getGraphContainer().getBaseTopology().getVertexNamespace();

        List<SearchProvider> providers = m_serviceManager.getServices(SearchProvider.class, null, new Properties());
        List<SearchResult> results = Lists.newArrayList();

        for(SearchProvider provider : providers) {
            if(provider.getSearchProviderNamespace().equals(namespace) || provider.contributesTo(namespace)){
                if(searchPrefix != null && provider.supportsPrefix(searchPrefix)) {
                    String queryOnly = query.replace(searchPrefix, "");
                    List<SearchResult> q = provider.query(getSearchQuery(queryOnly));
                    results.addAll(q);
                    if(m_suggestionMap.containsKey(provider)){
                        m_suggestionMap.get(provider).addAll(q);
                    } else{
                        m_suggestionMap.putAll(provider, q);
                    }

                } else{
                    List<SearchResult> q = provider.query(getSearchQuery(query));
                    results.addAll(q);
                    if (m_suggestionMap.containsKey(provider)) {
                        m_suggestionMap.get(provider).addAll(q);
                    } else {
                        m_suggestionMap.putAll(provider, q);
                    }
                }
            }

        }

        return mapToSuggestions(results);
    }


    private SearchQuery getSearchQuery(String query) {
        SearchQuery searchQuery;
        if(query.equals("*")){
            searchQuery = new AllSearchQuery(query);
        } else{
            searchQuery = new ContainsSearchQuery(query);
        }
        return searchQuery;
    }

    public String getQueryPrefix(String query){
        String prefix = null;
        if(query.contains("=")){
            prefix = query.substring(0, query.indexOf('=') + 1);
        }
        return prefix;
    }

    private List<SearchSuggestion> mapToSuggestions(List<SearchResult> searchResults) {
        return Lists.transform(searchResults, new Function<SearchResult, SearchSuggestion>(){
            @Override
            public SearchSuggestion apply(SearchResult searchResult) {
                return mapToSearchSuggestion(searchResult);
            }
        });

    }

    private VertexRef mapToVertexRef(SearchSuggestion suggestion) {
        return new AbstractVertexRef(suggestion.getNamespace(), suggestion.getId(), suggestion.getLabel());
    }

    private SearchSuggestion mapToSearchSuggestion(SearchResult searchResult) {
        SearchSuggestion suggestion = new SearchSuggestion();
        suggestion.setNamespace(searchResult.getNamespace());
        suggestion.setId(searchResult.getId());
        suggestion.setLabel(searchResult.getLabel());

        return suggestion;
    }

    private SearchSuggestion mapToSearchSuggestion(VertexRef vertexRef) {
        SearchSuggestion suggestion = new SearchSuggestion();
        suggestion.setNamespace(vertexRef.getNamespace());
        suggestion.setId(vertexRef.getId());
        suggestion.setLabel(vertexRef.getLabel());

        return suggestion;
    }

    @Override
    protected SearchBoxState getState() {
        return (SearchBoxState) super.getState();
    }

    private void init() {
        registerRpc(m_rpc);
        getState().immediate = true;
        setWidth(250.0f, Unit.PIXELS);
        setImmediate(true);

        m_suggestionMap = HashMultimap.create();
        updateTokenFieldList(m_operationContext.getGraphContainer());
    }

    @Override
    public void selectionChanged(SelectionContext selectionContext) {
        List<SearchSuggestion> selected = Lists.newArrayList();

        //List<VertexRef> vertexRefs = Lists.newArrayList(selectionContext.getSelectedVertexRefs());
        //getState().setSelected(mapToSuggestions(vertexRefs));

    }

    @Override
    public void graphChanged(GraphContainer graphContainer) {
        updateTokenFieldList(graphContainer);
    }

    private void updateTokenFieldList(GraphContainer graphContainer) {
        List<SearchSuggestion> suggestions = Lists.newArrayList();
        FocusNodeHopCriteria nodeCriteria = VertexHopGraphProvider.getFocusNodeHopCriteriaForContainer(graphContainer);
        for (VertexRef vRef : nodeCriteria.getVertices()) {
            suggestions.add(mapToSearchSuggestion(vRef));
        }

        Criteria[] criterium = graphContainer.getCriteria();


        for (Criteria criteria : criterium) {
            try {
                if(criteria != nodeCriteria){
                    VertexHopCriteria crit = (VertexHopCriteria) criteria;
                    SearchSuggestion suggestion = new SearchSuggestion();
                    suggestion.setId(crit.getId());
                    suggestion.setLabel(crit.getLabel());
                    suggestion.setNamespace(crit.getNamespace());

                    suggestions.add(suggestion);
                }
            } catch (ClassCastException e) {
            }
        }

        getState().setFocused(suggestions);
    }

    private class ContainsSearchQuery extends AbstractSearchQuery implements SearchQuery {
        public ContainsSearchQuery(String query) {
            super(query);
        }

        @Override
        public boolean matches(String str) {
            return str.toLowerCase().contains(getQueryString().toLowerCase());
        }
    }

    private class AllSearchQuery extends AbstractSearchQuery{

        public AllSearchQuery(String queryString) {
            super(queryString);
        }

        @Override
        public boolean matches(String str) {
            return true;
        }
    }
}
