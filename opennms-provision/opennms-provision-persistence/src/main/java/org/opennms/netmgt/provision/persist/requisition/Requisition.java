/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2009-2011 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2011 The OpenNMS Group, Inc.
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

//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.0.3-b01-fcs 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2009.01.29 at 01:15:48 PM EST 
//


package org.opennms.netmgt.provision.persist.requisition;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.ValidationException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.opennms.core.utils.LogUtils;
import org.opennms.core.xml.ValidateUsing;
import org.opennms.netmgt.provision.persist.OnmsNodeRequisition;
import org.opennms.netmgt.provision.persist.RequisitionVisitor;


/**
 * <p>Requisition class.</p>
 *
 * @author ranger
 * @version $Id: $
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name="model-import")
@ValidateUsing("model-import.xsd")
public class Requisition implements Serializable, Comparable<Requisition> {

    private static final long serialVersionUID = 2099710942679236239L;

    @XmlTransient
    private Map<String, OnmsNodeRequisition> m_nodeReqs = new LinkedHashMap<String, OnmsNodeRequisition>();
    
    @XmlElement(name="node")
    protected List<RequisitionNode> m_nodes = new ArrayList<RequisitionNode>();
    
    @XmlAttribute(name="date-stamp")
    protected XMLGregorianCalendar m_dateStamp;
    
    @XmlAttribute(name="foreign-source")
    protected String m_foreignSource = "imported:";
    
    @XmlAttribute(name="last-import")
    protected XMLGregorianCalendar m_lastImport;

    /**
     * <p>getNode</p>
     *
     * @param foreignId a {@link java.lang.String} object.
     * @return a {@link org.opennms.netmgt.provision.persist.requisition.RequisitionNode} object.
     */
    public RequisitionNode getNode(String foreignId) {
        if (m_nodes != null) {
            for (RequisitionNode n : m_nodes) {
                if (n.getForeignId().equals(foreignId)) {
                	LogUtils.debugf(this, "returning node '%s' for foreign id '%s'", n, foreignId);
                    return n;
                }
            }
        }
        return null;
    }

    /**
     * <p>removeNode</p>
     *
     * @param node a {@link org.opennms.netmgt.provision.persist.requisition.RequisitionNode} object.
     */
    public void deleteNode(RequisitionNode node) {
        if (m_nodes != null) {
            Iterator<RequisitionNode> i = m_nodes.iterator();
            while (i.hasNext()) {
                RequisitionNode n = i.next();
                if (n.getForeignId().equals(node.getForeignId())) {
                    i.remove();
                    break;
                }
            }
        }
    }

    /**
     * <p>deleteNode</p>
     *
     * @param foreignId a {@link java.lang.String} object.
     */
    public void deleteNode(final String foreignId) {
        if (m_nodes != null) {
        	final Iterator<RequisitionNode> i = m_nodes.iterator();
            while (i.hasNext()) {
                final RequisitionNode n = i.next();
                if (n.getForeignId().equals(foreignId)) {
                    i.remove();
                    break;
                }
            }
        }
    }

    /* backwards-compat with ModelImport */
    /**
     * <p>getNode</p>
     *
     * @return an array of {@link org.opennms.netmgt.provision.persist.requisition.RequisitionNode} objects.
     */
    @XmlTransient
    public RequisitionNode[] getNode() {
        return getNodes().toArray(new RequisitionNode[] {});
    }

    /**
     * <p>getNodes</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<RequisitionNode> getNodes() {
        return m_nodes;
    }

    /**
     * <p>setNodes</p>
     *
     * @param nodes a {@link java.util.List} object.
     */
    public void setNodes(final List<RequisitionNode> nodes) {
        m_nodes = nodes;
        updateNodeCache();
    }

    /**
     * <p>insertNode</p>
     *
     * @param node a {@link org.opennms.netmgt.provision.persist.requisition.RequisitionNode} object.
     */
    public void insertNode(final RequisitionNode node) {
        updateNodeCacheIfNecessary();
        if (m_nodeReqs.containsKey(node.getForeignId())) {
        	final RequisitionNode n = m_nodeReqs.get(node.getForeignId()).getNode();
            m_nodes.remove(n);
        }
        m_nodes.add(0, node);
        m_nodeReqs.put(node.getForeignId(), new OnmsNodeRequisition(getForeignSource(), node));
    }

    /**
     * <p>putNode</p>
     *
     * @param node a {@link org.opennms.netmgt.provision.persist.requisition.RequisitionNode} object.
     */
    public void putNode(final RequisitionNode node) {
        updateNodeCacheIfNecessary();
        if (m_nodeReqs.containsKey(node.getForeignId())) {
        	final RequisitionNode n = m_nodeReqs.get(node.getForeignId()).getNode();
            m_nodes.remove(n);
        }
        m_nodes.add(node);
        m_nodeReqs.put(node.getForeignId(), new OnmsNodeRequisition(getForeignSource(), node));
    }

    /**
     * <p>getDateStamp</p>
     *
     * @return a {@link javax.xml.datatype.XMLGregorianCalendar} object.
     */
    public XMLGregorianCalendar getDateStamp() {
        return m_dateStamp;
    }

    /**
     * <p>setDateStamp</p>
     *
     * @param value a {@link javax.xml.datatype.XMLGregorianCalendar} object.
     */
    public void setDateStamp(final XMLGregorianCalendar value) {
        m_dateStamp = value;
    }

    /**
     * <p>updateDateStamp</p>
     */
    public void updateDateStamp() {
        try {
            m_dateStamp = DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar());
        } catch (final DatatypeConfigurationException e) {
            LogUtils.warnf(this, e, "unable to update datestamp");
        }
    }

    /**
     * <p>getForeignSource</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getForeignSource() {
        if (m_foreignSource == null) {
            return "imported:";
        } else {
            return m_foreignSource;
        }
    }

    /**
     * <p>setForeignSource</p>
     *
     * @param value a {@link java.lang.String} object.
     */
    public void setForeignSource(final String value) {
        m_foreignSource = value;
    }

    /**
     * <p>getLastImport</p>
     *
     * @return a {@link javax.xml.datatype.XMLGregorianCalendar} object.
     */
    public XMLGregorianCalendar getLastImport() {
        return m_lastImport;
    }

    /**
     * <p>setLastImport</p>
     *
     * @param value a {@link javax.xml.datatype.XMLGregorianCalendar} object.
     */
    public void setLastImport(final XMLGregorianCalendar value) {
        m_lastImport = value;
    }

    /**
     * Update the last imported stamp to the current date and time
     */
    public void updateLastImported() {
        try {
            m_lastImport = DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar());
        } catch (final DatatypeConfigurationException e) {
            LogUtils.warnf(this, e, "unable to update last import datestamp");
        }
    }

    // Exists only to be compatible with old (1.6!) imports XSD
    @XmlAttribute(name="non-ip-interfaces")
    public boolean getNonIpInterfaces() {
        return false;
    }

    public void setNonIpInterfaces(final boolean nii) {
        LogUtils.warnf(this, "The non-ip-interfaces field was deprecated in 1.6, and removed in 1.8.  Ignored.");
    }

    // Exists only to be compatible with old (1.6!) imports XSD
    @XmlAttribute(name="non-ip-snmp-primary")
    public String getNonIpSnmpPrimary() {
        return "N";
    }
    
    public void setNonIpSnmpPrimary(final String nisp) {
        LogUtils.warnf(this, "The non-ip-snmp-primary field was deprecated in 1.6, and removed in 1.8.  Ignored.");
    }

    /* Start non-JAXB methods */

    /**
     * <p>Constructor for Requisition.</p>
     */
    public Requisition() {
        updateNodeCache();
        updateDateStamp();
    }

    /**
     * <p>Constructor for Requisition.</p>
     *
     * @param foreignSource a {@link java.lang.String} object.
     */
    public Requisition(final String foreignSource) {
        this();
        m_foreignSource = foreignSource;
    }
    
    private void updateNodeCache() {
        m_nodeReqs.clear();
        if (m_nodes != null) {
            for (final RequisitionNode n : m_nodes) {
                m_nodeReqs.put(n.getForeignId(), new OnmsNodeRequisition(getForeignSource(), n));
            }
        }
    }
    
	private void updateNodeCacheIfNecessary() {
		if (m_nodes != null && m_nodeReqs.size() != m_nodes.size()) {
            updateNodeCache();
        }
	}

    /**
     * <p>visit</p>
     *
     * @param visitor a {@link org.opennms.netmgt.provision.persist.RequisitionVisitor} object.
     */
    public void visit(final RequisitionVisitor visitor) {
        updateNodeCacheIfNecessary();

        if (visitor == null) {
            LogUtils.warnf(this, "no visitor specified!");
            return;
        }

        visitor.visitModelImport(this);
        
        for (final OnmsNodeRequisition nodeReq : m_nodeReqs.values()) {
            nodeReq.visit(visitor);
        }
        
        visitor.completeModelImport(this);
    }

    /**
     * <p>getNodeRequistion</p>
     *
     * @param foreignId a {@link java.lang.String} object.
     * @return a {@link org.opennms.netmgt.provision.persist.OnmsNodeRequisition} object.
     */
    public OnmsNodeRequisition getNodeRequistion(final String foreignId) {
        updateNodeCacheIfNecessary();
        return m_nodeReqs.get(foreignId);
    }
    
    /**
     * <p>getNodeCount</p>
     *
     * @return a int.
     */
    @XmlTransient
    public int getNodeCount() {
        return (m_nodes == null) ? 0 : m_nodes.size();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("foreign-source", getForeignSource())
            .append("date-stamp", getDateStamp())
            .append("last-import", getLastImport())
            .append("nodes", getNodes())
            .toString();
    }

    /**
     * <p>compareTo</p>
     *
     * @param obj a {@link org.opennms.netmgt.provision.persist.requisition.Requisition} object.
     * @return a int.
     */
    @Override
    public int compareTo(final Requisition obj) {
    	return getForeignSource().compareTo(obj.getForeignSource());
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Requisition) {
        	final Requisition other = (Requisition) obj;
        	return getForeignSource().equals(other.getForeignSource());
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
    	return getForeignSource().hashCode();
      }

    /**
     * Make sure that no data in the requisition is inconsistent.  Nodes should be unique,
     * interfaces should be unique per node, etc.
     */
    public void validate() throws ValidationException {
    	final Map<String,Integer> foreignSourceCounts = new HashMap<String,Integer>();
    	final Set<String> errors = new HashSet<String>();

    	for (final RequisitionNode node : m_nodes) {
    		final String foreignId = node.getForeignId();
			Integer count = foreignSourceCounts.get(foreignId);
			foreignSourceCounts.put(foreignId, count == null? 1 : ++count);
    	}
    	
    	for (final String foreignId : foreignSourceCounts.keySet()) {
    		final Integer count = foreignSourceCounts.get(foreignId);
    		if (count > 1) {
    			errors.add( foreignId + " (" + count + " found)");
    		}
    	}
    	
    	if (errors.size() > 0) {
    		final StringBuilder sb = new StringBuilder();
    		sb.append("Duplicate nodes found on foreign source ").append(getForeignSource()).append(": ");
    		final Iterator<String> it = errors.iterator();
    		while (it.hasNext()) {
    			final String error = it.next();
    			sb.append(error);
    			if (it.hasNext()) {
    				sb.append(", ");
    			}
    		}
    		throw new ValidationException(sb.toString());
    	}
    }
}
