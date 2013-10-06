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


import com.google.common.base.Function;
import com.google.common.collect.*;
import com.vaadin.ui.AbstractComponent;
import org.opennms.features.topology.api.*;
import org.opennms.features.topology.api.support.VertexHopGraphProvider;
import org.opennms.features.topology.api.support.VertexHopGraphProvider.VertexHopCriteria;
import org.opennms.features.topology.api.topo.*;
import org.opennms.features.topology.app.internal.gwt.client.SearchBoxServerRpc;
import org.opennms.features.topology.app.internal.gwt.client.SearchBoxState;
import org.opennms.features.topology.app.internal.gwt.client.SearchSuggestion;
import org.opennms.osgi.OnmsServiceManager;

import java.util.*;


public class SearchBox extends AbstractComponent implements SelectionListener, GraphContainer.ChangeListener {

    Multimap<SearchProvider, SearchSuggestion> m_suggestionMap;
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
        public void selectSuggestion(List<SearchSuggestion> selectedSuggestion) {

            Multiset<SearchProvider> keys = m_suggestionMap.keys();
            for(SearchProvider key : keys){
                if(m_suggestionMap.get(key).contains(selectedSuggestion.get(0))){
                    key.getSelectionOperation().execute(mapToVertexRefs(selectedSuggestion), m_operationContext);

                    break;
                }
            }

            VertexHopCriteria criteria = VertexHopGraphProvider.getVertexHopCriteriaForContainer(m_operationContext.getGraphContainer());
            criteria.addAll(mapToVertexRefs(selectedSuggestion));
            m_operationContext.getGraphContainer().redoLayout();
        }

        @Override
        public void removeSelected(SearchSuggestion searchSuggestion) {
            SelectionManager selectionManager = m_operationContext.getGraphContainer().getSelectionManager();
            selectionManager.deselectVertexRefs(Lists.newArrayList(mapToVertexRef(searchSuggestion)));
        }

        @Override
        public void removeFocused(SearchSuggestion searchSuggestion) {

            for (Criteria criteria : m_operationContext.getGraphContainer().getCriteria()) {
                try {
                    VertexHopCriteria hopCriteria = (VertexHopCriteria)criteria;
                    hopCriteria.remove(mapToVertexRef(searchSuggestion));

                    // If there are no remaining focus vertices in the criteria, then
                    // just remove it completely.
                    if (hopCriteria.size() == 0) {
                        m_operationContext.getGraphContainer().removeCriteria(criteria);
                    }
                } catch (ClassCastException e) {}
            }

            m_operationContext.getGraphContainer().redoLayout();
        }

    };

    public SearchBox(OnmsServiceManager serviceManager, OperationContext operationContext) {
        m_serviceManager = serviceManager;
        m_operationContext = operationContext;
        init();
    }

    private List<SearchSuggestion> getQueryResults(final String query) {
        m_suggestionMap.clear();
        List<SearchProvider> providers = m_serviceManager.getServices(SearchProvider.class, null, new Properties());


        SearchQuery searchQuery;
        if(query.equals("*")){
            searchQuery = new AllSearchQuery(query);
        } else{
            searchQuery = new ContainsSearchQuery(query);
        }

        for(SearchProvider provider : providers) {
            m_suggestionMap.putAll(provider, mapToSuggestions(provider.query(searchQuery)));
        }

        Collection<SearchSuggestion> values = m_suggestionMap.values();
        return Lists.newArrayList(values);
    }

    private List<SearchSuggestion> mapToSuggestions(List<VertexRef> vertexRefs) {
        return Lists.transform(vertexRefs, new Function<VertexRef, SearchSuggestion>(){
            @Override
            public SearchSuggestion apply(VertexRef vertexRef) {
                return mapToSearchSuggestion(vertexRef);
            }
        });

    }

    private List<VertexRef> mapToVertexRefs(List<SearchSuggestion> suggestion){
        return Lists.transform(suggestion, new Function<SearchSuggestion, VertexRef>(){
            @Override
            public VertexRef apply(SearchSuggestion input) {
                return mapToVertexRef(input);
            }
        });
    }

    private VertexRef mapToVertexRef(SearchSuggestion suggestion) {
        return new AbstractVertexRef(suggestion.getNamespace(), suggestion.getVertexKey(), suggestion.getLabel());
    }

    private SearchSuggestion mapToSearchSuggestion(VertexRef vertexRef) {
        SearchSuggestion suggestion = new SearchSuggestion();
        suggestion.setNamespace(vertexRef.getNamespace());
        suggestion.setVertexKey(vertexRef.getId());
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
    }

    @Override
    public void selectionChanged(SelectionContext selectionContext) {
        List<SearchSuggestion> selected = Lists.newArrayList();

        List<VertexRef> vertexRefs = Lists.newArrayList(selectionContext.getSelectedVertexRefs());
        getState().setSelected(mapToSuggestions(vertexRefs));

    }

    @Override
    public void graphChanged(GraphContainer graphContainer) {
        VertexHopCriteria hopCriteria = VertexHopGraphProvider.getVertexHopCriteriaForContainer(graphContainer);

        getState().setFocused(mapToSuggestions(Lists.newArrayList(hopCriteria.getVertices())));

    }

    private class ContainsSearchQuery extends AbstractSearchQuery implements SearchQuery {
        public ContainsSearchQuery(String query) {
            super(query);
        }

        @Override
        public boolean matches(VertexRef vertexRef) {
            return vertexRef.getLabel().toLowerCase().contains(getQueryString().toLowerCase());
        }
    }

    private class AllSearchQuery extends AbstractSearchQuery{

        public AllSearchQuery(String queryString) {
            super(queryString);
        }

        @Override
        public boolean matches(VertexRef vertexRef) {
            return true;
        }
    }
}
