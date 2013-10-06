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

package org.opennms.features.topology.api.topo;

import org.junit.Test;
import org.opennms.features.topology.api.support.AbstractSearchSelectionOperation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AbstractSearchProviderTest {

    public class TestVertexRef implements VertexRef{

        private final String m_id;
        private final String m_label;

        public TestVertexRef(String id, String label){
            m_id = id;
            m_label = label;
        }

        @Override
        public String getId() {
            return m_id;
        }

        @Override
        public String getNamespace() {
            return "nodes";
        }

        @Override
        public String getLabel() {
            return m_label;
        }

        @Override
        public int compareTo(Ref o) {
            return 0;
        }
    }

    public class ContainsMatcher extends AbstractSearchQuery {

        public ContainsMatcher(String queryString) {
            super(queryString);
        }

        @Override
        public boolean matches(VertexRef vertexRef) {
            return vertexRef.getLabel().contains(getQueryString());
        }
    }

    public class ExactMatcher extends AbstractSearchQuery {

        public ExactMatcher(String queryString) {
            super(queryString);
        }

        @Override
        public boolean matches(VertexRef vertexRef) {
            return vertexRef.getLabel().matches(getQueryString());
        }
    }

    @Test
    public void testSearchProvider(){
        SearchQuery containsQuery = new ContainsMatcher("node");
        SearchQuery exactQuery = new ExactMatcher("node-label-1");

        SearchProvider searchProvider1 = createSearchProvider();

        assertEquals(10, searchProvider1.query(containsQuery).size());
        assertEquals(1, searchProvider1.query(exactQuery).size());
    }

    private SearchProvider createSearchProvider() {
        return new SearchProvider() {

            List<VertexRef> m_vertexRefs = getVertexRefs();

            @Override
            public List<VertexRef> query(SearchQuery searchQuery) {
                List<VertexRef> verts = new ArrayList<VertexRef>();
                for (VertexRef vertexRef : m_vertexRefs) {
                    if (searchQuery.matches(vertexRef)) {
                        verts.add(vertexRef);
                    }
                }
                return verts;
            }

            @Override
            public AbstractSearchSelectionOperation getSelectionOperation() {
                return null;
            }

        };
    }

    private List<VertexRef> getVertexRefs(){
        List<VertexRef> vertexRefs = new ArrayList<VertexRef>();

        for(int i = 0; i < 10; i++) {
            vertexRefs.add(new TestVertexRef("" + i, "node-label-" + i));
        }

        return vertexRefs;
    }

}
