/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2013 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2013 The OpenNMS Group, Inc.
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

package org.opennms.features.topology.plugins.browsers;

import com.vaadin.data.Property;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.ColumnGenerator;
import com.vaadin.ui.themes.BaseTheme;
import org.opennms.features.topology.api.SelectionListener;
import org.opennms.features.topology.api.VerticesUpdateManager;
import org.opennms.features.topology.api.topo.AbstractVertexRef;
import org.opennms.features.topology.api.topo.VertexRef;
import org.opennms.osgi.EventProxy;
import org.opennms.osgi.EventProxyAware;

import java.util.*;

public class NodeSelectionLinkGenerator implements ColumnGenerator, EventProxyAware {

	private static final long serialVersionUID = -1072007643387089006L;

	private final String m_nodeIdProperty;
    private final String m_nodeLabelProperty;
	private final ColumnGenerator m_generator;

	/**
	 * TODO: Fix concurrent access to this field
	 */
	private Collection<SelectionListener> m_selectionListeners = new HashSet<SelectionListener>();
    private EventProxy m_eventProxy;

    public NodeSelectionLinkGenerator(String nodeIdProperty, String nodeLabelProperty) {
		this(nodeIdProperty, nodeLabelProperty, new ToStringColumnGenerator());
	}

	private NodeSelectionLinkGenerator(String nodeIdProperty, String nodeLabelProperty, ColumnGenerator generator) {
		m_nodeIdProperty = nodeIdProperty;
        m_nodeLabelProperty = nodeLabelProperty;
		m_generator = generator;
	}

	@Override
	public Object generateCell(final Table source, final Object itemId, Object columnId) {
		final Property<Integer> nodeIdProperty = source.getContainerProperty(itemId, m_nodeIdProperty);
		Object cellValue = m_generator.generateCell(source, itemId, columnId);
		if (cellValue == null) {
			return null;
		} else {
			if (nodeIdProperty.getValue() == null) {
				return cellValue;
			} else {
				Button button = new Button(cellValue.toString());
				button.setStyleName(BaseTheme.BUTTON_LINK);
				button.setDescription(nodeIdProperty.getValue().toString());
				button.addClickListener(new ClickListener() {
					@Override
					public void buttonClick(ClickEvent event) {
                        Integer nodeId = nodeIdProperty.getValue();
                        String nodeLabel = (String)source.getContainerProperty(itemId, m_nodeLabelProperty).getValue();
                        fireVertexUpdatedEvent(nodeId, nodeLabel);
                    }
                });
				return button;
			}
		}
	}

    protected void fireVertexUpdatedEvent(Integer nodeId, String nodeLabel) {
        Set<VertexRef> vertexRefs = new HashSet<VertexRef>();
        VertexRef vRef = new AbstractVertexRef("nodes", String.valueOf(nodeId), nodeLabel);
        vertexRefs.add(vRef);
        getEventProxy().fireEvent(new VerticesUpdateManager.VerticesUpdateEvent(vertexRefs));
    }

    public void setEventProxy(EventProxy eventProxy) {
        this.m_eventProxy = eventProxy;
    }

    public EventProxy getEventProxy() {
        return m_eventProxy;
    }
}
