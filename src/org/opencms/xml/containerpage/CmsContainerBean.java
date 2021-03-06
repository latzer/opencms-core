/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.xml.containerpage;

import org.opencms.util.CmsCollectionsGenericWrapper;
import org.opencms.util.CmsUUID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.Transformer;

/**
 * One container of a container page.<p>
 * 
 * @since 8.0
 */
public class CmsContainerBean {

    /** A lazy initialized map that describes if a certain element if part of this container. */
    private transient Map<CmsUUID, Boolean> m_containsElement;

    /** The id's of of all elements in this container. */
    private transient List<CmsUUID> m_elementIds;

    /** The container elements. */
    private final List<CmsContainerElementBean> m_elements;

    /** The maximal number of elements in the container. */
    private int m_maxElements;

    /** The container name. */
    private final String m_name;

    /** The container type. */
    private final String m_type;

    /** The container width set by the rendering container tag. */
    private String m_width;

    /** 
     * Creates a new container bean.<p> 
     * 
     * @param name the container name
     * @param type the container type
     * @param maxElements the maximal number of elements in the container
     * @param elements the elements
     **/
    public CmsContainerBean(String name, String type, int maxElements, List<CmsContainerElementBean> elements) {

        m_name = name;
        m_type = type;
        m_maxElements = maxElements;
        m_elements = (elements == null
        ? Collections.<CmsContainerElementBean> emptyList()
        : Collections.unmodifiableList(elements));
    }

    /** 
     * Creates a new container bean with an unlimited number of elements.<p> 
     * 
     * @param name the container name
     * @param type the container type
     * @param elements the elements
     **/
    public CmsContainerBean(String name, String type, List<CmsContainerElementBean> elements) {

        this(name, type, -1, elements);
    }

    /**
     * Returns <code>true</code> if the element with the provided id is contained in this container.<p>
     *  
     * @param elementId the element id to check
     * 
     * @return <code>true</code> if the element with the provided id is contained in this container
     */
    public boolean containsElement(CmsUUID elementId) {

        return getElementIds().contains(elementId);
    }

    /**
     * Returns a lazy initialized map that describes if a certain element if part of this container.<p>
     * 
     * @return a lazy initialized map that describes if a certain element if part of this container
     */
    public Map<CmsUUID, Boolean> getContainsElement() {

        if (m_containsElement == null) {
            m_containsElement = CmsCollectionsGenericWrapper.createLazyMap(new Transformer() {

                public Object transform(Object input) {

                    return Boolean.valueOf(containsElement((CmsUUID)input));
                }
            });
        }
        return m_containsElement;
    }

    /**
     * Returns the id's of all elements in this container.<p>
     *
     * @return the id's of all elements in this container
     */
    public List<CmsUUID> getElementIds() {

        if (m_elementIds == null) {
            m_elementIds = new ArrayList<CmsUUID>(m_elements.size());
            for (CmsContainerElementBean element : m_elements) {
                m_elementIds.add(element.getId());
            }
        }
        return m_elementIds;
    }

    /**
     * Returns the elements in this container.<p>
     *
     * @return the elements in this container
     */
    public List<CmsContainerElementBean> getElements() {

        return m_elements;
    }

    /**
     * Returns the maximal number of elements in this container.<p>
     *
     * @return the maximal number of elements in this container
     */
    public int getMaxElements() {

        return m_maxElements;
    }

    /**
     * Returns the name of this container.<p>
     *
     * @return the name of this container
     */
    public String getName() {

        return m_name;
    }

    /**
     * Returns the type of this container.<p>
     *
     * @return the type of this container
     */
    public String getType() {

        return m_type;
    }

    /**
     * Returns the container width set by the rendering container tag.<p>
     *
     * @return the container width
     */
    public String getWidth() {

        return m_width;
    }

    /**
     * Sets the maximal number of elements in the container.<p>
     *
     * @param maxElements the maximal number of elements to set
     */
    public void setMaxElements(int maxElements) {

        m_maxElements = maxElements;
    }

    /**
     * Sets the client side render with of this container.<p>
     *
     * @param width the client side render with of this container
     */
    public void setWidth(String width) {

        m_width = width;
    }
}
