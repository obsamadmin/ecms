/*
 * Copyright (C) 2003-2009 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.wcm.connector;

import org.apache.commons.lang.StringUtils;
import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.ecm.connector.fckeditor.FCKMessage;
import org.exoplatform.ecm.connector.fckeditor.FCKUtils;
import org.exoplatform.ecm.utils.lock.LockUtil;
import org.exoplatform.ecm.utils.text.Text;
import org.exoplatform.services.cms.documents.AutoVersionService;
import org.exoplatform.services.cms.documents.DocumentService;
import org.exoplatform.services.cms.impl.Utils;
import org.exoplatform.services.cms.jcrext.activity.ActivityCommonService;
import org.exoplatform.services.cms.mimetype.DMSMimeTypeResolver;
import org.exoplatform.services.cms.templates.TemplateService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.core.ExtendedSession;
import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.services.wcm.publication.WCMPublicationService;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.exoplatform.upload.UploadResource;
import org.exoplatform.upload.UploadService;
import org.exoplatform.upload.UploadService.UploadLimit;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.JSONValue;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by The eXo Platform SAS
 * Author : Tran Nguyen Ngoc
 * ngoc.tran@exoplatform.com
 * Sep 4, 2009
 */
public class FileUploadHandler {

  /** Logger */  
  private static final Log LOG = ExoLogger.getLogger(FileUploadHandler.class.getName());

  /** The Constant UPLOAD_ACTION. */
  public final static String UPLOAD_ACTION = "upload";

  /** The Constant PROGRESS_ACTION. */
  public final static String PROGRESS_ACTION = "progress";

  /** The Constant ABORT_ACTION. */
  public final static String ABORT_ACTION = "abort";

  /** The Constant DELETE_ACTION. */
  public final static String DELETE_ACTION = "delete";

  /** The Constant SAVE_ACTION. */
  public final static String SAVE_ACTION = "save";
  
  /** The Constant SAVE_NEW_VERSION_ACTION. */
  public final static String SAVE_NEW_VERSION_ACTION = "saveNewVersion";
  
  /** The Constant CHECK_EXIST. */
  public final static String CHECK_EXIST= "exist";
  
  /** The Constant REPLACE. */
  public final static String REPLACE= "replace";

  public final static String CREATE_VERSION = "createVersion";

  /** The Constant KEEP_BOTH. */
  public final static String KEEP_BOTH= "keepBoth";

  /** The Constant LAST_MODIFIED_PROPERTY. */
  private static final String LAST_MODIFIED_PROPERTY = "Last-Modified";

  /** The Constant UPLOAD_DOC_NEW_APP. */
  public static final String UPLOAD_DOC_NEW_APP          = "exo.upload.doc.newApp";

  /** The Constant String UPLOAD_DOC_OLD_APP. */
  public static final String UPLOAD_DOC_OLD_APP          = "exo.upload.doc.oldApp";

  private static final String  OLD_APP              = "oldApp";

  /** The Constant IF_MODIFIED_SINCE_DATE_FORMAT. */
  private static final String IF_MODIFIED_SINCE_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z";
  
  public final static String POST_CREATE_CONTENT_EVENT = "CmsService.event.postCreate";

  private final String CONNECTOR_BUNDLE_LOCATION                 = "locale.wcm.resources.WCMResourceBundleConnector";
  private final String AUTOVERSION_ERROR_MIME_TYPE                  = "DocumentAutoVersion.msg.WrongMimeType";
  private static final String FILE_DECODE_REGEX = "%(?![0-9a-fA-F]{2})";

  /** The document service. */
  private DocumentService documentService;

  /** The repository service. */
  private RepositoryService repositoryService;

  /** The upload service. */
  private UploadService uploadService;
  
  /** The listener service. */
  ListenerService listenerService;
  
  private ActivityCommonService   activityService;

  /** The fck message. */
  private FCKMessage fckMessage;
  
  /** The uploadIds - time Map */
  private Map<String, Long> uploadIdTimeMap;
  
  /** The maximal life time for an upload */
  private long UPLOAD_LIFE_TIME;

  /**
   * Instantiates a new file upload handler.
   */
  public FileUploadHandler() {
    uploadService = WCMCoreUtils.getService(UploadService.class);
    listenerService = WCMCoreUtils.getService(ListenerService.class);
    activityService = WCMCoreUtils.getService(ActivityCommonService.class);
    documentService = WCMCoreUtils.getService(DocumentService.class);
    repositoryService = WCMCoreUtils.getService(RepositoryService.class);
    fckMessage = new FCKMessage();
    uploadIdTimeMap = new Hashtable<String, Long>();
    UPLOAD_LIFE_TIME = System.getProperty("MULTI_UPLOAD_LIFE_TIME") == null ? 600 ://10 minutes
                                        Long.parseLong(System.getProperty("MULTI_UPLOAD_LIFE_TIME"));
  }

  /**
   * Upload
   * @param servletRequest The request to upload file
   * @param uploadId Upload Id
   * @param limit Limit size of upload file
   * @return
   * @throws Exception
   */
  public Response upload(HttpServletRequest servletRequest, String uploadId, Integer limit) throws Exception{
    uploadService.addUploadLimit(uploadId, limit);
    uploadService.createUploadResource(servletRequest);
    uploadIdTimeMap.put(uploadId, System.currentTimeMillis());
    CacheControl cacheControl = new CacheControl();
    cacheControl.setNoCache(true);
    cacheControl.setNoStore(true);
    DateFormat dateFormat = new SimpleDateFormat(IF_MODIFIED_SINCE_DATE_FORMAT);
    
    //create ret
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.newDocument();
    Element rootElement = doc.createElement("html");
    Element head = doc.createElement("head");
    Element body = doc.createElement("body");
    rootElement.appendChild(head);
    rootElement.appendChild(body);
    doc.appendChild(rootElement);
    
    return Response.ok(new DOMSource(doc), MediaType.TEXT_XML)
                   .cacheControl(cacheControl)
                   .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
                   .build();
  }
  
  /**
   * Check status of uploaded file.
   * If any problem while uploading, error message is returned.
   * Returning null means no problem happen.
   * 
   * @param uploadId upload ID
   * @param language language for getting message
   * @return Response message is returned if any problem while uploading.
   * @throws Exception
   */
  public Response checkStatus(String uploadId, String language) throws Exception {
    CacheControl cacheControl = new CacheControl();
    cacheControl.setNoCache(true);
    cacheControl.setNoStore(true);
    DateFormat dateFormat = new SimpleDateFormat(IF_MODIFIED_SINCE_DATE_FORMAT);
    
    if ((StringUtils.isEmpty(uploadId)) || (uploadService.getUploadResource(uploadId) == null)) return null;
    
    // If file size exceed limit, return message
    if (UploadResource.FAILED_STATUS == uploadService.getUploadResource(uploadId).getStatus()) {
      
      // Remove upload Id
      uploadService.removeUploadResource(uploadId);
      uploadIdTimeMap.remove(uploadId);
      // Get message warning upload exceed limit
      String uploadLimit = String.valueOf(uploadService.getUploadLimits().get(uploadId).getLimit());
      Document fileExceedLimit =
          fckMessage.createMessage(FCKMessage.FILE_EXCEED_LIMIT,
                                   FCKMessage.ERROR,
                                   language,
                                   new String[]{uploadLimit});
      
      return Response.ok(new DOMSource(fileExceedLimit), MediaType.TEXT_XML)
                      .cacheControl(cacheControl)
                      .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
                      .build();
    }
    
    return null;
  }
  
  /**
   * Control.
   *
   * @param uploadId the upload id
   * @param action the action
   *
   * @return the response
   *
   * @throws Exception the exception
   */
  public Response control(String uploadId, String action) throws Exception {
    CacheControl cacheControl = new CacheControl();
    cacheControl.setNoCache(true);
    cacheControl.setNoStore(true);
    DateFormat dateFormat = new SimpleDateFormat(IF_MODIFIED_SINCE_DATE_FORMAT);
    
    if (FileUploadHandler.PROGRESS_ACTION.equals(action)) {
      Document currentProgress = getProgress(uploadId);
      return Response.ok(new DOMSource(currentProgress), MediaType.TEXT_XML)
                     .cacheControl(cacheControl)
                     .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
                     .build();
    } else if (FileUploadHandler.ABORT_ACTION.equals(action)) {
      uploadService.removeUploadResource(uploadId);
      uploadIdTimeMap.remove(uploadId);
      return Response.ok(null, MediaType.TEXT_XML)
                     .cacheControl(cacheControl)
                     .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
                     .build();
    } else if (FileUploadHandler.DELETE_ACTION.equals(action)) {
      uploadService.removeUploadResource(uploadId);
      uploadIdTimeMap.remove(uploadId);
      return Response.ok(null, MediaType.TEXT_XML)
                     .cacheControl(cacheControl)
                     .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
                     .build();
    }
    return Response.status(HTTPStatus.BAD_REQUEST)
                   .cacheControl(cacheControl)
                   .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
                   .build();
  }

  /**
   * checks if file already existed in parent folder
   *
   * @param parent the parent
   * @param fileName the file name
   * @return the response
   *
   * @throws Exception the exception
   */
  public Response checkExistence(Node parent, String fileName) throws Exception {
    DMSMimeTypeResolver resolver = DMSMimeTypeResolver.getInstance();
    CacheControl cacheControl = new CacheControl();
    cacheControl.setNoCache(true);
    DateFormat dateFormat = new SimpleDateFormat(IF_MODIFIED_SINCE_DATE_FORMAT);
    
    //create ret
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document fileExistence = builder.newDocument();
    fileName = Text.escapeIllegalJcrChars(fileName);
    fileName = fileName.replaceAll(FILE_DECODE_REGEX, "%25");
    fileName = URLDecoder.decode(fileName,"UTF-8");
    fileName = fileName.replaceAll(FILE_DECODE_REGEX, "-");
    Element rootElement = fileExistence.createElement(
                              parent.hasNode(fileName) ? "Existed" : "NotExisted");
    if(parent.hasNode(fileName)){
      Node existNode = parent.getNode(fileName);
      if(existNode.isNodeType(NodetypeConstant.MIX_VERSIONABLE)){
        rootElement.appendChild(fileExistence.createElement("Versioned"));
      }
    }
    if(parent.isNodeType(NodetypeConstant.NT_FILE) && 
        resolver.getMimeType(parent.getName()).equals(resolver.getMimeType(fileName))){
      rootElement.appendChild(fileExistence.createElement("CanVersioning"));
    }
    fileExistence.appendChild(rootElement);
    //return ret;
    return Response.ok(new DOMSource(fileExistence), MediaType.TEXT_XML)
                   .cacheControl(cacheControl)
                   .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
                   .build();
  }
  
  /**
   * Clean name using Transliterator
   * @param fileName original file name
   * 
   * @return Response
   */
  public Response cleanName(String fileName) throws Exception {
    CacheControl cacheControl = new CacheControl();
    cacheControl.setNoCache(true);
    DateFormat dateFormat = new SimpleDateFormat(IF_MODIFIED_SINCE_DATE_FORMAT);
    
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document cleanedFilename = builder.newDocument(); 
    fileName = Utils.cleanNameWithAccents(fileName);
    Element rootElement = cleanedFilename.createElement("name");
    cleanedFilename.appendChild(rootElement);
    rootElement.setTextContent(fileName);
    return Response.ok(new DOMSource(cleanedFilename), MediaType.TEXT_XML)
            .cacheControl(cacheControl)
            .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
            .build();
  }
  
  /**
   * Save as nt file.
   *
   * @param parent the parent
   * @param uploadId the upload id
   * @param fileName the file name
   * @param language the language
   * @param source the source
   *
   * @return the response
   *
   * @throws Exception the exception
   */
  public Response saveAsNTFile(String workspaceName,
                               Node parent,
                               String uploadId,
                               String fileName,
                               String language,
                               String source,
                               String siteName,
                               String userId) throws Exception {
    return saveAsNTFile(workspaceName, parent, uploadId, fileName, language,source, siteName, userId, KEEP_BOTH);
  }
  
  /**
   * Save as nt file.
   *
   * @param parent the parent
   * @param uploadId the upload id
   * @param fileName the file name
   * @param language the language
   * @param source the source
   *
   * @return the response
   *
   * @throws Exception the exception
   */
  public Response saveAsNTFile(String workspaceName,
                               Node parent,
                               String uploadId,
                               String fileName,
                               String language,
                               String source,
                               String siteName,
                               String userId,
                               String existenceAction) throws Exception {
    return saveAsNTFile(workspaceName, parent, uploadId, fileName, language,source , siteName, userId, existenceAction,false);
  }
  /**
   * Save already uploaded file (identified by uploadId) as nt file.
   * 
   * 'synchronized' is used to avoid JCR index corruption when uploading massively files.
   * The JCR index (that uses a very old version of Apache lucene)
   * gets corrupted when doing massive modifications on the same parent node.
   * 
   * Thus here we have made this central method as synchronized to avoid corruption
   * and ensure Data consistency in favor of performances. The upload itself is not synchronized,
   * we still be able to upload concurrently using org.exoplatform.web.handler.UploadHandler
   * but the commit of uploaded file to be stored on JCR is made using this method, thus this critical
   * operation has been made synchronized.
   *
   * @param parent the parent
   * @param uploadId the upload id
   * @param fileName the file name
   * @param language the language
   * @param source the source
   *
   * @return the response
   *
   * @throws Exception the exception
   */
  public synchronized Response saveAsNTFile(String workspaceName,
                               Node parent,
                               String uploadId,
                               String fileName,
                               String language,
                               String source,
                               String siteName,
                               String userId,
                               String existenceAction,
                               boolean isNewVersion) throws Exception {
    fileName = Utils.cleanNameWithAccents(fileName);
    String exoTitle = fileName;
    fileName = Utils.cleanName(fileName);
    try {
      CacheControl cacheControl = new CacheControl();
      cacheControl.setNoCache(true);
      UploadResource resource = uploadService.getUploadResource(uploadId);
      DateFormat dateFormat = new SimpleDateFormat(IF_MODIFIED_SINCE_DATE_FORMAT);
      if (parent == null) {
        Document fileNotUploaded = fckMessage.createMessage(FCKMessage.FILE_NOT_UPLOADED,
                                                            FCKMessage.ERROR,
                                                            language,
                                                            null);
        return Response.ok(new DOMSource(fileNotUploaded), MediaType.TEXT_XML)
                       .cacheControl(cacheControl)
                       .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
                       .build();
      }
      if (!FCKUtils.hasAddNodePermission(parent)) {
        Object[] args = { parent.getPath() };
        Document message = fckMessage.createMessage(FCKMessage.FILE_UPLOAD_RESTRICTION,
                                                    FCKMessage.ERROR,
                                                    language,
                                                    args);
        return Response.ok(new DOMSource(message), MediaType.TEXT_XML)
                       .cacheControl(cacheControl)
                       .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
                       .build();
      }
      if ((fileName == null) || (fileName.length() == 0)) {
        fileName = resource.getFileName();
      }
      //add lock token
      if(parent.isLocked()) {
        parent.getSession().addLockToken(LockUtil.getLockToken(parent));
      }
      if (parent.hasNode(fileName)) {
  //      Object args[] = { fileName, parent.getPath() };
  //      Document fileExisted = fckMessage.createMessage(FCKMessage.FILE_EXISTED,
  //                                                      FCKMessage.ERROR,
  //                                                      language,
  //                                                      args);
  //      return Response.ok(new DOMSource(fileExisted), MediaType.TEXT_XML)
  //                     .cacheControl(cacheControl)
  //                     .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
  //                     .build();
        if (REPLACE.equals(existenceAction)) {
          //Broadcast the event when user move node to Trash
          ListenerService listenerService =  WCMCoreUtils.getService(ListenerService.class);
          listenerService.broadcast(ActivityCommonService.FILE_REMOVE_ACTIVITY, parent, parent.getNode(fileName));
          parent.getNode(fileName).remove();
          parent.save();        
        }
      }
      AutoVersionService autoVersionService = WCMCoreUtils.getService(AutoVersionService.class);
      String location = resource.getStoreLocation();
      //save node with name=fileName
      Node file = null;
      Node jcrContent=null;
      boolean fileCreated = false;
      DMSMimeTypeResolver mimeTypeResolver = DMSMimeTypeResolver.getInstance();
      String mimetype = mimeTypeResolver.getMimeType(resource.getFileName());
      String nodeName = fileName;
      int count = 0;
      if(!CREATE_VERSION.equals(existenceAction) ||
              (!parent.hasNode(fileName) && !CREATE_VERSION.equals(existenceAction))) {
        if(parent.isNodeType(NodetypeConstant.NT_FILE)){
          String mimeTypeParent = mimeTypeResolver.getMimeType(parent.getName());
          if(mimetype != mimeTypeParent){
            ResourceBundleService resourceBundleService = WCMCoreUtils.getService(ResourceBundleService.class);
            ResourceBundle resourceBundle = resourceBundleService.getResourceBundle(CONNECTOR_BUNDLE_LOCATION, new Locale(language));
            String errorMsg = resourceBundle.getString(AUTOVERSION_ERROR_MIME_TYPE);
            errorMsg = errorMsg.replace("{0}", StringUtils.escape("<span style='font-weight:bold;'>" + parent.getName() + "</span>"));
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("error_type", "ERROR_MIMETYPE");
            jsonObject.put("error_message", errorMsg);
            return Response.serverError().entity(jsonObject.toString()).build();
          }
          parent = parent.getParent();
        }
        do {
          try {
            file = parent.addNode(nodeName, FCKUtils.NT_FILE);
            fileCreated = true;
          } catch (ItemExistsException e) {//sameNameSibling is not allowed
            nodeName = increaseName(fileName, ++count);
          }
        } while (!fileCreated);
        //--------------------------------------------------------
        if(!file.isNodeType(NodetypeConstant.MIX_REFERENCEABLE)) {
          file.addMixin(NodetypeConstant.MIX_REFERENCEABLE);
        }

        if(!file.isNodeType(NodetypeConstant.MIX_COMMENTABLE))
          file.addMixin(NodetypeConstant.MIX_COMMENTABLE);

        if(!file.isNodeType(NodetypeConstant.MIX_VOTABLE))
          file.addMixin(NodetypeConstant.MIX_VOTABLE);

        if(!file.isNodeType(NodetypeConstant.MIX_I18N))
          file.addMixin(NodetypeConstant.MIX_I18N);

        if(!file.hasProperty(NodetypeConstant.EXO_TITLE)) {
          file.setProperty(NodetypeConstant.EXO_TITLE, exoTitle);
        }
        jcrContent = file.addNode("jcr:content","nt:resource");
      }else if(parent.hasNode(nodeName)){
        file = parent.getNode(nodeName);
        autoVersionService.autoVersion(file,isNewVersion);
        jcrContent = file.hasNode("jcr:content")?file.getNode("jcr:content"):file.addNode("jcr:content","nt:resource");
      }else if(parent.isNodeType(NodetypeConstant.NT_FILE)){
        file = parent;
        autoVersionService.autoVersion(file,isNewVersion);
        jcrContent = file.hasNode("jcr:content")?file.getNode("jcr:content"):file.addNode("jcr:content","nt:resource");
      }

      jcrContent.setProperty("jcr:lastModified", new GregorianCalendar());
      jcrContent.setProperty("jcr:data", new BufferedInputStream(new FileInputStream(new File(location))));
      jcrContent.setProperty("jcr:mimeType", mimetype);
      if(fileCreated) {
        file.getParent().save();
        autoVersionService.autoVersion(file,isNewVersion);
      }
      //parent.getSession().refresh(true); // Make refreshing data
      //parent.save();
      uploadService.removeUploadResource(uploadId);
      uploadIdTimeMap.remove(uploadId);
      WCMPublicationService wcmPublicationService = WCMCoreUtils.getService(WCMPublicationService.class);
      wcmPublicationService.updateLifecyleOnChangeContent(file, siteName, userId);
     
      if (activityService.isBroadcastNTFileEvents(file) && !CREATE_VERSION.equals(existenceAction)) {
        listenerService.broadcast(ActivityCommonService.FILE_CREATED_ACTIVITY, null, file);
      }
      file.save();

      // return uploaded file
      Document doc = getUploadedFile(workspaceName, file, mimetype);
      String eventName = source.equals(OLD_APP) ? UPLOAD_DOC_OLD_APP : UPLOAD_DOC_NEW_APP;
      try {
        listenerService.broadcast(eventName, userId, file);
      } catch (Exception e) {
        LOG.error("Error broadcast upload document event", e);
      }
      return Response.ok(new DOMSource(doc), MediaType.TEXT_XML)
          .cacheControl(cacheControl)
          .header(LAST_MODIFIED_PROPERTY, dateFormat.format(new Date()))
          .build();
    } catch (Exception exc) {
      LOG.error(exc.getMessage(), exc);
      return Response.serverError().entity(exc.getMessage()).build();
    }
  }

  private Document getUploadedFile(String workspaceName, Node file, String mimetype) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.newDocument();
    Element rootElement = doc.createElement("file");

    LinkedHashMap<String, String> previewBreadcrumb = new LinkedHashMap<>();
    ExtendedSession session = null;
    try {
      session = (ExtendedSession) WCMCoreUtils.getSystemSessionProvider().getSession("collaboration", repositoryService.getCurrentRepository());
      Node node = session.getNodeByIdentifier(file.getUUID());
      previewBreadcrumb = documentService.getFilePreviewBreadCrumb(node);
    } catch (Exception e ) {
      LOG.error("Error while getting file node " + file.getUUID(), e);
    } finally {
      if (session != null) {
        session.logout();
      }
    }

    String downloadUrl = getDownloadUrl(workspaceName, file.getPath());
    String url = getUrl(file.getPath());
    String lastEditor = getStringProperty(file, "exo:lastModifier");
    String date = getStringProperty(file, "exo:dateModified");

    rootElement.setAttribute("UUID", file.getUUID());
    rootElement.setAttribute("title", file.getName());
    rootElement.setAttribute("path", file.getPath());
    rootElement.setAttribute("mimetype", mimetype);
    rootElement.setAttribute("previewBreadcrumb", JSONValue.toJSONString(previewBreadcrumb));
    rootElement.setAttribute("downloadUrl", downloadUrl);
    rootElement.setAttribute("url", url);
    rootElement.setAttribute("lastEditor", lastEditor);
    rootElement.setAttribute("date", date);

    List<AccessControlEntry> permissions = ((NodeImpl) file).getACL().getPermissionEntries();
    rootElement.setAttribute("acl", JSONValue.toJSONString(getFileACL(permissions)));

    long size = file.getNode("jcr:content").getProperty("jcr:data").getLength();
    rootElement.setAttribute("size", String.valueOf(size));

    doc.appendChild(rootElement);
    return doc;
  }

  private JSONObject getFileACL(List<AccessControlEntry> permissions) throws JSONException {
    Boolean canRead = permissions.stream().anyMatch(perm -> perm.getPermission().equals(PermissionType.READ));
    Boolean canEdit = permissions.stream().anyMatch(perm -> perm.getPermission().equals(PermissionType.SET_PROPERTY));
    Boolean canRemove = permissions.stream().anyMatch(perm -> perm.getPermission().equals(PermissionType.REMOVE));
    JSONObject acl = new JSONObject();
    acl.put("canEdit", canEdit);
    acl.put("canRead", canRead);
    acl.put("canRemove", canRemove);
    return acl;
  }

  private String getStringProperty(Node node, String propertyName) throws RepositoryException {
    if (node.hasProperty(propertyName)) {
      return node.getProperty(propertyName).getString();
    }
    return "";
  }

  protected String getUrl(String nodePath) {
    String url = "";
    try {
      url = documentService.getLinkInDocumentsApp(nodePath);
    } catch (Exception e) {
      LOG.error("Cannot get url of document " + nodePath, e);
    }
    return url;
  }

  private String getDownloadUrl(String workspace, String nodePath) {
    String restContextName =  WCMCoreUtils.getRestContextName();

    String repositoryName = getRepositoryName();

    StringBuffer downloadUrl = new StringBuffer();
    downloadUrl.append('/').append(restContextName).append("/jcr/").
      append(repositoryName).append('/').
      append(workspace).append(nodePath);
    return downloadUrl.toString();
  }

  protected String getRepositoryName() {
    try {
      return repositoryService.getCurrentRepository().getConfiguration().getName();
    } catch (RepositoryException e) {
      LOG.debug("Cannot get repository name", e);
      return "repository";
    }
  }
  
  public boolean isDocumentNodeType(Node node) throws Exception {
    TemplateService templateService = WCMCoreUtils.getService(TemplateService.class);
    return templateService.isManagedNodeType(node.getPrimaryNodeType().getName());
  }
  
  /**
   * increase the file name (not extension).
   * @param origin the original name
   * @param count the number add to file name
   * @return the new increased file name
   */
  private String increaseName(String origin, int count) {
    int index = origin.indexOf('.');
    if (index == -1) return origin + count;
    return origin.substring(0, index) + count + origin.substring(index);
  }
  
  /**
   * get number of files uploading 
   * @return number of files uploading
   */
  public long getUploadingFileCount() {
    removeDeadUploads();
    return uploadIdTimeMap.size();
  }

  /**
   * removes dead uploads
   */
  private void removeDeadUploads() {
    Set<String> removedIds = new HashSet<String>();
    for (String id : uploadIdTimeMap.keySet()) {
      if ((System.currentTimeMillis() - uploadIdTimeMap.get(id)) > UPLOAD_LIFE_TIME * 1000) {
        removedIds.add(id);
      }
    }
    for (String id : removedIds) {
      uploadIdTimeMap.remove(id);
    }
  }
  /**
   * Gets the progress.
   *
   * @param uploadId the upload id
   *
   * @return the progress
   *
   * @throws Exception the exception
   */
  private Document getProgress(String uploadId) throws Exception {
    UploadResource resource = uploadService.getUploadResource(uploadId);
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.newDocument();
    Double percent = 0.0;
    if(resource != null) {
      if (resource.getStatus() == UploadResource.UPLOADING_STATUS) {
        percent = (resource.getUploadedSize() * 100) / resource.getEstimatedSize();
      } else {
        percent = 100.0;
      }
    }
    Element rootElement = doc.createElement("UploadProgress");
    rootElement.setAttribute("uploadId", uploadId);
    rootElement.setAttribute("fileName", resource == null ? "" : resource.getFileName());
    rootElement.setAttribute("percent", percent.intValue() + "");
    rootElement.setAttribute("uploadedSize", resource == null ? "0" : resource.getUploadedSize() + "");
    rootElement.setAttribute("totalSize", resource == null ? "0" : resource.getEstimatedSize() + "");
    rootElement.setAttribute("fileType", resource == null ? "null" : resource.getMimeType() + "");
    UploadLimit limit = uploadService.getUploadLimits().get(uploadId);
    if (limit != null) {
      rootElement.setAttribute("limit", limit.getLimit() + "");
      rootElement.setAttribute("unit", limit.getUnit() + "");
    }
    doc.appendChild(rootElement);
    return doc;
  }
  
  /**
   * returns a DOMSource object containing given message
   * @param name the message
   * @return DOMSource object
   * @throws Exception
   */
  private DOMSource createDOMResponse(String name, String mimeType) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.newDocument();
    Element rootElement = doc.createElement(name);
    rootElement.setAttribute("mimetype", mimeType);
    doc.appendChild(rootElement);
    return new DOMSource(doc);
  }
  
}
