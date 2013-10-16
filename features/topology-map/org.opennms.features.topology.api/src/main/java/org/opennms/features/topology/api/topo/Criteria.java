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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The interface is extended by plugin developers to allow the setting of criteria for their Providers
 * 
 * @author brozow
 *
 */
public abstract class Criteria {

    public enum ElementType { GRAPH, VERTEX, EDGE;}

    private volatile AtomicBoolean m_criteriaDirty = new AtomicBoolean(Boolean.TRUE);

	/**
	 * This criteria applies to only providers of the indicated type
	 */
	public abstract ElementType getType();

	/**
	 * This criteria only applies to providers for this namespace
	 */
	public abstract String getNamespace();


    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    public void resetDirty() {
        setDirty(false);
    }

    public boolean isDirty() {
        synchronized (m_criteriaDirty) {
            return m_criteriaDirty.get();
        }
    }

    protected void setDirty(boolean isDirty) {
        synchronized (m_criteriaDirty) {
            m_criteriaDirty.set(isDirty);
        }
    }
}
