/*
 * File   : $Source: /alkacon/cvs/opencms/src/org/opencms/loader/Attic/CmsXmlTemplateLoader.java,v $
 * Date   : $Date: 2003/07/18 19:03:49 $
 * Version: $Revision: 1.2 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Mananagement System
 *
 * Copyright (C) 2002 - 2003 Alkacon Software (http://www.alkacon.com)
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

package org.opencms.loader;

import com.opencms.boot.I_CmsLogChannels;
import com.opencms.core.A_OpenCms;
import com.opencms.core.CmsException;
import com.opencms.core.I_CmsConstants;
import com.opencms.core.I_CmsRequest;
import com.opencms.core.I_CmsResponse;
import com.opencms.file.CmsFile;
import com.opencms.file.CmsObject;
import com.opencms.file.CmsRequestContext;
import com.opencms.file.CmsResource;
import com.opencms.template.A_CmsXmlContent;
import com.opencms.template.CmsRootTemplate;
import com.opencms.template.CmsTemplateCache;
import com.opencms.template.CmsTemplateClassManager;
import com.opencms.template.CmsXmlControlFile;
import com.opencms.template.I_CmsTemplate;
import com.opencms.template.I_CmsTemplateCache;
import com.opencms.template.I_CmsXmlTemplate;
import com.opencms.template.cache.CmsElementCache;
import com.opencms.template.cache.CmsElementDefinition;
import com.opencms.template.cache.CmsElementDefinitionCollection;
import com.opencms.template.cache.CmsElementDescriptor;
import com.opencms.template.cache.CmsUri;
import com.opencms.template.cache.CmsUriDescriptor;
import com.opencms.template.cache.CmsUriLocator;
import com.opencms.util.Encoder;
import com.opencms.util.Utils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Implementation of the {@link I_CmsResourceLoader} and 
 * the {@link com.opencms.launcher.I_CmsLauncher} interface for 
 * XMLTemplates.<p>
 * 
 * This implementation can deliver XMLTemplates directly since it extends
 * the {@link com.opencms.launcher.CmsXmlLauncher}. It is also usable to include 
 * XMLTemplates as sub-elements on a JSP page since it implements the
 * {@link I_CmsResourceLoader} interface. 
 *
 * @author  Alexander Kandzior (a.kandzior@alkacon.com)
 *
 * @version $Revision: 1.2 $
 * @since FLEX alpha 1
 */
public class CmsXmlTemplateLoader implements I_CmsResourceLoader {
    
    /** The id of this loader */
    public static final int C_RESOURCE_LOADER_ID = 3;
    
    /** Magic elemet replace name */
    public static final String C_ELEMENT_REPLACE = "_CMS_ELEMENTREPLACE";

    /** Value of the filesystem counter, when the last template clear cache was done */
    private static long m_lastFsCounterTemplate = 0;

    /** Value of the filesystem counter, when the last XML file clear cache was done */
    private static long m_lastFsCounterFile = 0;

    /** The template cache that holds all cached templates */
    protected static I_CmsTemplateCache m_templateCache = new CmsTemplateCache();

    /** Flag for debugging output. Set to 9 for maximum verbosity. */ 
    private static final int DEBUG = 0;
        
    /**
     * The constructor of the class is empty and does nothing.
     */
    public CmsXmlTemplateLoader() {
        // NOOP
    }
    
    /**
     * @see org.opencms.loader.I_CmsResourceLoader#getLoaderId()
     */
    public int getLoaderId() {
        return C_RESOURCE_LOADER_ID;
    }      
        
    /** 
     * Destroy this ResourceLoder, this is a NOOP so far.  
     */
    public void destroy() {
        // NOOP
    }
    
    /**
     * Return a String describing the ResourceLoader,
     * which is <code>"The OpenCms default resource loader for XMLTemplates"</code>.<p>
     * 
     * @return a describing String for the ResourceLoader 
     */
    public String getResourceLoaderInfo() {
        return "The OpenCms default resource loader for XMLTemplates";
    }   
    
    /** 
     * Initialize the ResourceLoader, the OpenCms parameter is
     * saved here for later access to <code>generateOutput()</code>.<p>
     * 
     * @param openCms used to access <code>generateOutput()</code> later
     */
    public void init(A_OpenCms openCms) {
        if (I_CmsLogChannels.C_LOGGING && A_OpenCms.isLogging(I_CmsLogChannels.C_FLEX_LOADER)) 
            A_OpenCms.log(I_CmsLogChannels.C_FLEX_LOADER, this.getClass().getName() + " initialized!");     
    }
    
    /**
     * @see org.opencms.loader.I_CmsResourceLoader#load(com.opencms.file.CmsObject, com.opencms.file.CmsFile, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public void load(CmsObject cms, CmsFile file, HttpServletRequest req, HttpServletResponse res) 
    throws ServletException, IOException {   
        try { 
            processXmlTemplate(cms, file);
        } catch (CmsException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }
    
    /**
     * @see org.opencms.loader.I_CmsResourceLoader#export(com.opencms.file.CmsObject, com.opencms.file.CmsFile)
     */
    public void export(CmsObject cms, CmsFile file) throws CmsException {
        processXmlTemplate(cms, file);    
    }
    
    /**
     * Processes the XmlTemplates and writes the result to 
     * the apropriate output stream, which is obtained from the request 
     * context of the cms object.<p>
     *
     * @param cms the cms context object
     * @param file the selected resource to be shown
     * @throws CmsException if something goes wrong
     */
    private void processXmlTemplate(CmsObject cms, CmsFile file) throws CmsException {

        // first some debugging output.
        if ((DEBUG > 0) && I_CmsLogChannels.C_PREPROCESSOR_IS_LOGGING && A_OpenCms.isLogging()) {
            A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_CRITICAL, getClassName() + "Launcher started for " + file.getResourceName());
        }

        // check all values to be valid
        String errorMessage = null;
        if (file == null) {
            errorMessage = "Got \"null\" CmsFile object. :-(";
        }
        if (cms == null) {
            errorMessage = "Actual cms object missing";
        }
        if (errorMessage != null) {
            if (I_CmsLogChannels.C_PREPROCESSOR_IS_LOGGING && A_OpenCms.isLogging()) {
                A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_CRITICAL, getClassName() + errorMessage);
            } 
            throw new CmsException(errorMessage, CmsException.C_LAUNCH_ERROR);
        }

        // Check the clearcache parameter
        String clearcache = cms.getRequestContext().getRequest().getParameter("_clearcache");
        
        // Clear launcher caches if this is required
        clearLauncherCache(cms, 
            ((clearcache != null) && ("all".equals(clearcache) || "file".equals(clearcache))),
            ((clearcache != null) && ("all".equals(clearcache) || "template".equals(clearcache))));
        
        // get the CmsRequest
        I_CmsRequest req = cms.getRequestContext().getRequest();
        byte[] result = null;
        result = generateOutput(cms, file, req);
        if (result != null) {
            writeBytesToResponse(cms, result);
        }
    }    
        
    /**
     * Does the job of including an XMLTemplate 
     * as a sub-element of a JSP or other resource loaders.<p>
     * 
     * @param cms used to access the OpenCms VFS
     * @param file the reqested JSP file resource in the VFS
     * @param req the current request
     * @param res the current response
     * 
     * @throws ServletException might be thrown in the process of including the JSP 
     * @throws IOException might be thrown in the process of including the JSP 
     * 
     * @see com.opencms.flex.cache.CmsFlexRequestDispatcher
     */    
    public void service(CmsObject cms, CmsResource file, ServletRequest req, ServletResponse res)
    throws ServletException, IOException {
        long timer1;
        if (DEBUG > 0) {
            timer1 = System.currentTimeMillis();        
            System.err.println("============ CmsXmlTemplateLoader loading: " + cms.readAbsolutePath(file));            
            System.err.println("CmsXmlTemplateLoader.service() cms uri is: " + cms.getRequestContext().getUri());            
        }
        // save the original context settings
        String rnc = cms.getRequestContext().getEncoding().trim();
        String oldUri = cms.getRequestContext().getUri();
        I_CmsRequest cms_req = cms.getRequestContext().getRequest();        
        HttpServletRequest originalreq = (HttpServletRequest)cms_req.getOriginalRequest();
        try {                        
            // get the CmsRequest
            byte[] result = null;
            com.opencms.file.CmsFile fx = cms.readFile(cms.readAbsolutePath(file));            
            // care about encoding issues
            String dnc = A_OpenCms.getDefaultEncoding().trim();
            String enc = cms.readProperty(cms.readAbsolutePath(fx), I_CmsConstants.C_PROPERTY_CONTENT_ENCODING, true, dnc).trim();
            // fake the called URI (otherwise XMLTemplate / ElementCache would not work)
            cms.getRequestContext().setUri(cms.readAbsolutePath(fx));            
            cms_req.setOriginalRequest(req);
            cms.getRequestContext().setEncoding(enc);      
            if (DEBUG > 1) {
                System.err.println("CmsXmlTemplateLoader.service(): Encodig set to " + cms.getRequestContext().getEncoding());
                System.err.println("CmsXmlTemplateLoader.service(): Uri set to " + cms.getRequestContext().getUri());
            }
            // process the included XMLTemplate
            result = generateOutput(cms, fx, cms_req);                                    
            // append the result to the output stream
            if (result != null) {
                // Encoding project:
                // The byte array must internally be encoded in the OpenCms
                // default encoding. It will be converted to the requested encoding 
                // on the most top-level JSP element
                result = Encoder.changeEncoding(result, enc, dnc);
                if (DEBUG > 1) System.err.println("CmsXmlTemplateLoader.service(): encoding=" + enc + " requestEncoding=" + rnc + " defaultEncoding=" + dnc);                             
                res.getOutputStream().write(result);
            }        
        }  catch (Exception e) {
            if (DEBUG > 0) e.printStackTrace(System.err);
            throw new ServletException("Error in CmsXmlTemplateLoader processing", e);       
        } finally {
            // restore the context settings
            cms_req.setOriginalRequest(originalreq);
            cms.getRequestContext().setEncoding(rnc);
            cms.getRequestContext().setUri(oldUri);
            if (DEBUG > 1) {
                System.err.println("CmsXmlTemplateLoader.service(): Encodig reset to " + cms.getRequestContext().getEncoding());
                System.err.println("CmsXmlTemplateLoader.service(): Uri reset to " + cms.getRequestContext().getUri());
            }
        }
        if (DEBUG > 0) {
            long timer2 = System.currentTimeMillis() - timer1;        
            System.err.println("============ CmsXmlTemplateLoader time delivering XmlTemplate for " + cms.readAbsolutePath(file) + ": " + timer2 + "ms");            
        }
    } 
    
    /**
     * Starts generating the output.
     * Calls the canonical root with the appropriate template class.
     *
     * @param cms CmsObject Object for accessing system resources
     * @param file CmsFile Object with the selected resource to be shown
     * @param req the CmsRequest
     * @return the generated output for the file
     * @throws CmsException if something goes wrong
     */
    protected byte[] generateOutput(CmsObject cms, CmsFile file, I_CmsRequest req) throws CmsException {
        byte[] output = null;

        // Hashtable for collecting all parameters.
        Hashtable newParameters = new Hashtable();
        String uri = cms.getRequestContext().getUri();

        // ladies and gentelman: and now for something completly different 
        String absolutePath = cms.readAbsolutePath(file);
        String templateProp = cms.readProperty(absolutePath, I_CmsConstants.C_XML_CONTROL_TEMPLATE_PROPERTY);
        String xmlTemplateContent = null;
        if (templateProp != null) {
            // i got a black magic template,
            StringBuffer buf = new StringBuffer(256);
            buf.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n");
            buf.append("<PAGE>\n<class>");
            buf.append(I_CmsConstants.C_XML_CONTROL_DEFAULT_CLASS);
            buf.append("</class>\n<masterTemplate>");
            // i got a black magic template,
            buf.append(templateProp);
            buf.append("</masterTemplate>\n<ELEMENTDEF name=\"body\">\n<CLASS>");
            buf.append(I_CmsConstants.C_XML_CONTROL_DEFAULT_CLASS);
            buf.append("</CLASS>\n<TEMPLATE>");
            // i got a black magic template got me so blind I can't see,
            buf.append(uri);
            buf.append("</TEMPLATE>\n</ELEMENTDEF>\n</PAGE>\n");              
            // i got a black magic template it's try'in to make a devil out of me...
            xmlTemplateContent = buf.toString();
            uri += I_CmsConstants.C_XML_CONTROL_FILE_SUFFIX; 
        }
        
        // Parameters used for element cache
        boolean elementCacheEnabled = cms.getRequestContext().isElementCacheEnabled();
        CmsElementCache elementCache = null;
        CmsUriDescriptor uriDesc = null;
        CmsUriLocator uriLoc = null;
        CmsUri cmsUri = null;

        String templateClass = null;
        String templateName = null;
        CmsXmlControlFile doc = null;

        if (elementCacheEnabled) {
            // Get the global element cache object
            elementCache = cms.getRequestContext().getElementCache();

            // Prepare URI Locator
            uriDesc = new CmsUriDescriptor(uri);
            uriLoc = elementCache.getUriLocator();
            cmsUri = uriLoc.get(uriDesc);
        }

        // check if printversion is requested
        String replace = req.getParameter(C_ELEMENT_REPLACE);
        boolean elementreplace = false;
        CmsElementDefinition replaceDef = null;
        if (replace != null) {
            int index = replace.indexOf(":");
            if (index != -1) {
                elementreplace = true;
                cmsUri = null;
                replaceDef = new CmsElementDefinition(replace.substring(0, index),
                                        I_CmsConstants.C_XML_CONTROL_DEFAULT_CLASS,
                                        replace.substring(index+1), null, new Hashtable());
            }
        }

        if ((cmsUri == null) || !elementCacheEnabled) {
            // Entry point to page file analysis.
            // For performance reasons this should only be done if the element
            // cache is not activated or if it's activated but no URI object could be found.

            // Parse the page file
            try {
                if (xmlTemplateContent == null) {
                    doc = new CmsXmlControlFile(cms, file);
                } else {
                    doc = new CmsXmlControlFile(cms, uri, xmlTemplateContent);
                }
            } catch (Exception e) {
                // there was an error while parsing the document.
                // No chance to go on here.
                handleException(cms, e, "There was an error while parsing XML page file " + cms.readAbsolutePath(file));
                return "".getBytes();
            }

            if (! elementCacheEnabled && (replaceDef != null)) {
                // Required to enable element replacement if element cache is disabled
                doc.setElementClass(replaceDef.getName(), replaceDef.getClassName());
                doc.setElementTemplate(replaceDef.getName(), replaceDef.getTemplateName());
            }

            // Get the names of the master template and the template class from
            // the parsed page file. Fall back to default value, if template class
            // is not defined
            templateClass = doc.getTemplateClass();
            if (templateClass == null || "".equals(templateClass)) {
                templateClass = this.getClass().getName();
            }
            if (templateClass == null || "".equals(templateClass)) {
                templateClass = I_CmsConstants.C_XML_CONTROL_DEFAULT_CLASS;
            }
            templateName = doc.getMasterTemplate();
            if (templateName != null && !"".equals(templateName)) {
                templateName = Utils.mergeAbsolutePath(cms.readAbsolutePath(file), templateName);
            }

            // Previously, the template class was loaded here.
            // We avoid doing this so early, since in element cache mode the template
            // class is not needed here.

            // Now look for parameters in the page file...
            // ... first the params of the master template...
            Enumeration masterTemplateParams = doc.getParameterNames();
            while (masterTemplateParams.hasMoreElements()) {
                String paramName = (String)masterTemplateParams.nextElement();
                String paramValue = doc.getParameter(paramName);
                newParameters.put(I_CmsConstants.C_ROOT_TEMPLATE_NAME + "." + paramName, paramValue);
            }

            // ... and now the params of all subtemplates
            Enumeration elementDefinitions = doc.getElementDefinitions();
            while (elementDefinitions.hasMoreElements()) {
                String elementName = (String)elementDefinitions.nextElement();
                if (doc.isElementClassDefined(elementName)) {
                    newParameters.put(elementName + "._CLASS_", doc.getElementClass(elementName));
                }
                if (doc.isElementTemplateDefined(elementName)) {
                    // need to check for the body template here so that non-XMLTemplate templates 
                    // like JSPs know where to find the body defined in the XMLTemplate
                    String template = doc.getElementTemplate(elementName);
                    if (xmlTemplateContent == null) {
                        template = doc.validateBodyPath(cms, template, file);
                    }
                    if (I_CmsConstants.C_XML_BODY_ELEMENT.equalsIgnoreCase(elementName)) {
                        // found body element
                        if (template != null) {
                            cms.getRequestContext().setAttribute(I_CmsConstants.C_XML_BODY_ELEMENT, template);
                        }
                    } 
                    newParameters.put(elementName + "._TEMPLATE_", template);
                }
                if (doc.isElementTemplSelectorDefined(elementName)) {
                    newParameters.put(elementName + "._TEMPLATESELECTOR_", doc.getElementTemplSelector(elementName));
                }
                Enumeration parameters = doc.getElementParameterNames(elementName);
                while (parameters.hasMoreElements()) {
                    String paramName = (String)parameters.nextElement();
                    String paramValue = doc.getElementParameter(elementName, paramName);
                    if (paramValue != null) {
                        newParameters.put(elementName + "." + paramName, paramValue);
                    } else {
                        if (I_CmsLogChannels.C_PREPROCESSOR_IS_LOGGING && A_OpenCms.isLogging()) {
                            A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_INFO, getClassName() + "Empty parameter \"" + paramName + "\" found.");
                        }
                    }
                }
            }
        }

        // URL parameters ary really dynamic.
        // We cannot store them in an element cache.
        // Therefore these parameters must be collected in ANY case!

        String datafor = req.getParameter("datafor");
        if (datafor == null) {
            datafor = "";
        } else {
            if (!"".equals(datafor)) {
                datafor = datafor + ".";
            }
        }
        Enumeration urlParameterNames = req.getParameterNames();
        while (urlParameterNames.hasMoreElements()) {
            String pname = (String)urlParameterNames.nextElement();
            String paramValue = req.getParameter(pname);
            if (paramValue != null) {
                if ((!"datafor".equals(pname)) && (!"_clearcache".equals(pname))) {
                    newParameters.put(datafor + pname, paramValue);
                }
            } else {
                if (I_CmsLogChannels.C_PREPROCESSOR_IS_LOGGING && A_OpenCms.isLogging()) {
                    A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_INFO, getClassName() + "Empty URL parameter \"" + pname + "\" found.");
                }
            }
        }

        if (elementCacheEnabled && cmsUri == null) {
            // ---- element cache stuff --------
            // No URI could be found in cache.
            // So create a new URI object with a start element and store it using the UriLocator
            CmsElementDescriptor elemDesc = new CmsElementDescriptor(templateClass, templateName);
            CmsElementDefinitionCollection eldefs = doc.getElementDefinitionCollection();
            if (elementreplace) {
                // we cant cach this
                eldefs.add(replaceDef);
                String readAccessGroup = cms.getReadingpermittedGroup(cms.getRequestContext().currentProject().getId(), templateName);
                cmsUri = new CmsUri(elemDesc, readAccessGroup, eldefs, Utils.isHttpsResource(cms, file));
            } else {
                cmsUri = new CmsUri(elemDesc, cms.getReadingpermittedGroup(
                            cms.getRequestContext().currentProject().getId(),
                            templateName), eldefs, Utils.isHttpsResource(cms, file));
                elementCache.getUriLocator().put(uriDesc, cmsUri);
            }
        }

        if (elementCacheEnabled) {
                // lets check if ssl is active
                if (cms.getMode() == I_CmsConstants.C_MODUS_ONLINE) {
                    String scheme = ((HttpServletRequest)cms.getRequestContext().getRequest().getOriginalRequest()).getScheme();
                    boolean httpsReq = "https".equalsIgnoreCase(scheme);
                    if (cmsUri.isHttpsResource() != httpsReq) {
                        if (httpsReq) {
                            //throw new CmsException(" "+cms.readPath(file)+" needs a http request", CmsException.C_HTTPS_PAGE_ERROR);
                        } else if (CmsObject.getStaticExportProperties().isStaticExportEnabled()
                                || "false_ssl".equals(CmsObject.getStaticExportProperties().getStaticExportEnabledValue())) {
                            // check if static export is enabled and value is not false_ssl
                            throw new CmsException(" "+cms.readAbsolutePath(file)+" needs a https request", CmsException.C_HTTPS_REQUEST_ERROR);
                        }
                    }
                }
                // now lets get the output
                if (elementreplace) {
                    output = cmsUri.callCanonicalRoot(elementCache, cms, newParameters);
                } else {
                    output = elementCache.callCanonicalRoot(cms, newParameters, uri);
                }
        } else {
            // ----- traditional stuff ------
            // Element cache is deactivated. So let's go on as usual.
            try {
                CmsFile masterTemplate = loadMasterTemplateFile(cms, templateName, doc);
                I_CmsTemplate tmpl = getTemplateClass(cms, templateClass);               
                if (!(tmpl instanceof I_CmsXmlTemplate)) {
                    String errorMessage = "Error in " + cms.readAbsolutePath(file) + ": " + templateClass + " is not a XML template class.";
                    if (I_CmsLogChannels.C_PREPROCESSOR_IS_LOGGING && A_OpenCms.isLogging()) {
                        A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_CRITICAL, getClassName() + errorMessage);
                    } 
                    throw new CmsException(errorMessage, CmsException.C_XML_WRONG_TEMPLATE_CLASS);
                }
                output = callCanonicalRoot(cms, (I_CmsTemplate)tmpl, masterTemplate, newParameters);
            } catch (CmsException e) {
                if (I_CmsLogChannels.C_PREPROCESSOR_IS_LOGGING && A_OpenCms.isLogging()) {
                    A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_INFO, "[CmsXmlLauncher] There were exceptions while generating output for " + cms.readAbsolutePath(file));
                    A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_INFO, "[CmsXmlLauncher] Clearing template file cache for this file.");
                }
                doc.removeFromFileCache();
                throw e;
            }
        }
        return output;
    }
    
    /**
     * Internal utility method for checking and loading a given template file.
     * @param cms CmsObject for accessing system resources.
     * @param templateName Name of the requestet template file.
     * @param doc CmsXmlControlFile object containig the parsed body file.
     * @return CmsFile object of the requested template file.
     * @throws CmsException if something goes wrong
     */
    private CmsFile loadMasterTemplateFile(CmsObject cms, String templateName, com.opencms.template.CmsXmlControlFile doc) throws CmsException {
        CmsFile masterTemplate = null;
        try {
            masterTemplate = cms.readFile(templateName);
        } catch (Exception e) {
            handleException(cms, e, "Cannot load master template " + templateName + ". ");
            doc.removeFromFileCache();
        }
        return masterTemplate;
    }

    /**
     * Utility method used by the launcher implementation to give control
     * to the CanonicalRoot.<p>
     * 
     * The CanonicalRoot will call the master template and return a byte array of the
     * generated output.<p>
     *
     * @param cms the cms context object
     * @param templateClass to generate the output of the master template
     * @param masterTemplate masterTemplate for the output
     * @param parameters contains all parameters for the template class
     * @return the generated output or null if there were errors
     * @throws CmsException if something goes wrong
     */
    private byte[] callCanonicalRoot(CmsObject cms, I_CmsTemplate templateClass, CmsFile masterTemplate, Hashtable parameters) throws CmsException {
        try {
            com.opencms.template.CmsRootTemplate root = (CmsRootTemplate)CmsTemplateClassManager.getClassInstance(cms, "com.opencms.template.CmsRootTemplate");
            return root.getMasterTemplate(cms, templateClass, masterTemplate, m_templateCache, parameters);
        } catch (Exception e) {
            // no document we could show...
            handleException(cms, e, "Received error while calling canonical root for requested file " + masterTemplate.getResourceName() + ". ");
        }
        return null;
    }

    /**
     * Returns the name of the class in the form "[ClassName] ",
     * to be used for error logging purposes.<p>
     * 
     * @return name of this class
     */
    private String getClassName() {
        String name = getClass().getName();
        return "[" + name.substring(name.lastIndexOf(".") + 1) + "] ";
    }

    /**
     * Calls the CmsClassManager to get an instance of the given template class.<p>
     * 
     * The returned object is checked to be an implementing class of the interface
     * I_CmsTemplate.
     * If the template cache of the template class is not yet set up, 
     * this will be done, too.<p>
     * 
     * @param cms the cms context object
     * @param classname name of the requested template class
     * @return instance of the template class
     * @throws CmsException if something goes wrong
     */
    private I_CmsTemplate getTemplateClass(CmsObject cms, String classname) throws CmsException {
        if (I_CmsLogChannels.C_PREPROCESSOR_IS_LOGGING && A_OpenCms.isLogging()) {
            A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_DEBUG, getClassName() + "Getting start template class " + classname + ". ");
        }
        Object o = CmsTemplateClassManager.getClassInstance(cms, classname);

        // Check, if the loaded class really is a OpenCms template class.

        // This is done be checking the implemented interface.
        if (!(o instanceof I_CmsTemplate)) {
            String errorMessage = "Class " + classname + " is no OpenCms template class.";
            if (I_CmsLogChannels.C_PREPROCESSOR_IS_LOGGING && A_OpenCms.isLogging()) {
                A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_CRITICAL, "[CmsTemplateClassManager] " + errorMessage);
            }
            throw new CmsException(errorMessage, CmsException.C_XML_NO_TEMPLATE_CLASS);
        }
        I_CmsTemplate cmsTemplate = (I_CmsTemplate)o;
        if (!cmsTemplate.isTemplateCacheSet()) {
            cmsTemplate.setTemplateCache(m_templateCache);
        }
        return cmsTemplate;
    }

    /**
     * Utility method to handle any occurence of an execption.<p>
     * 
     * If the Exception is NO CmsException (i.e. it was not detected previously)
     * it will be written to the logfile.<p>
     * 
     * If the current user is the anonymous user, no further exception will
     * be thrown, but a server error will be sent
     * (we want to prevent the user from seeing any exeptions).
     * Otherwise a new Exception will be thrown.
     * This will trigger the OpenCms error message box.<p>
     *
     * @param cms the cms context object
     * @param e Exception that should be handled
     * @param errorText error message that should be shown
     * @throws CmsException if 
     */
    private void handleException(CmsObject cms, Exception e, String errorText) throws CmsException {

        // Print out some error messages
        if (I_CmsLogChannels.C_PREPROCESSOR_IS_LOGGING && A_OpenCms.isLogging()) {
            A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_CRITICAL, getClassName() + errorText);
            A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_CRITICAL, getClassName() + "--> Exception: " + com.opencms.util.Utils.getStackTrace(e));
            A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_CRITICAL, getClassName() + "--> Cannot create output for this file. Must send error. Sorry.");
        }

        // if the user is "Guest", we send an servlet error,
        // otherwise we try to throw an exception.
        CmsRequestContext reqContext = cms.getRequestContext();
        if ((DEBUG == 0) && cms.anonymousUser().equals(reqContext.currentUser())) {
            throw new CmsException(errorText, CmsException.C_SERVICE_UNAVAILABLE, e);
        } else {
            if (e instanceof CmsException) {
                throw (CmsException)e;
            } else {
                throw new CmsException(errorText, CmsException.C_LAUNCH_ERROR, e);
            }
        }
    }
    
    /**
     * Compatibility method to ensure the legacy cache command line parameters
     * are still supported.<p>
     * 
     * @param cms an initialized CmsObject
     * @param clearFiles if true, A_CmsXmlContent cache is cleared
     * @param clearTemplates if true, internal template cache is cleared
     */
    private static void clearLauncherCache(CmsObject cms, boolean clearFiles, boolean clearTemplates) {
        long currentFsCounter = cms.getFileSystemChanges();
        if (clearFiles || (currentFsCounter > m_lastFsCounterFile)) {
            A_CmsXmlContent.clearFileCache();
            m_lastFsCounterFile = currentFsCounter;
        }
        if (clearTemplates || (currentFsCounter > m_lastFsCounterTemplate)) {
            m_templateCache.clearCache();
            m_lastFsCounterTemplate = currentFsCounter;
        }        
    }

    /**
     * Writes a given byte array to the HttpServletRespose output stream.<p>
     * 
     * @param cms an initialized CmsObject
     * @param result byte array that should be written.
     * @throws CmsException if something goes wrong
     */
    private void writeBytesToResponse(CmsObject cms, byte[] result) throws CmsException {
        try {
            I_CmsResponse resp = cms.getRequestContext().getResponse();
            if ((!cms.getRequestContext().isStreaming()) && result != null && !resp.isRedirected()) {
                // Only write any output to the response output stream if
                // the current request is neither redirected nor streamed.
                OutputStream out = resp.getOutputStream();

                resp.setContentLength(result.length);
                resp.setHeader("Connection", "keep-alive");
                out.write(result);
                out.close();
            }
        } catch (IOException ioe) {
            if (I_CmsLogChannels.C_LOGGING && A_OpenCms.isLogging(I_CmsLogChannels.C_OPENCMS_DEBUG)) {
                A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_DEBUG, getClassName() + "IO error while writing to response stream for " + cms.getRequestContext().getFileUri());
                A_OpenCms.log(I_CmsLogChannels.C_OPENCMS_DEBUG, getClassName() + ioe);
            }
        } catch (Exception e) {
            String errorMessage = "Cannot write output to HTTP response stream";
            handleException(cms, e, errorMessage);
        }
    }        
}
