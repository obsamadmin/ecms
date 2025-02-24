package org.exoplatform.social.ckeditor;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Session;
import javax.jcr.Workspace;

import org.apache.commons.io.IOUtils;
import org.exoplatform.ecms.uploads.HTMLUploadImageProcessorImpl;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;
import org.exoplatform.services.wcm.core.WCMService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.cms.link.LinkManager;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.upload.UploadResource;
import org.exoplatform.upload.UploadService;

import java.io.*;
import java.nio.file.Files;

@RunWith(MockitoJUnitRunner.class)
public class HTMLUploadImageProcessorTest {

  @Rule
  public TemporaryFolder uploadFolder = new TemporaryFolder();

  @Mock
  private PortalContainer portalContainer;

  @Mock
  private UploadService uploadService;

  @Mock
  private RepositoryService repositoryService;

  @Mock
  private ManageableRepository repository;

  @Mock
  private RepositoryEntry repositoryEntry;

  @Mock
  private Session session;

  @Mock
  private SessionProvider sessionProvider;

  @Mock
  private LinkManager linkManager;

  @Mock
  private SessionProviderService sessionProviderService;

  @Mock
  private NodeHierarchyCreator nodeHierarchyCreator;

  @Mock
  private WCMService wcmService;

  @Test
  public void shouldReturnSameContentWhenNoEmbeddedImage() throws Exception {
    // Given
    HTMLUploadImageProcessorImpl imageProcessor = new HTMLUploadImageProcessorImpl(portalContainer, uploadService, repositoryService, linkManager, sessionProviderService,nodeHierarchyCreator,wcmService);
    String content = "<p>content with no images</p>";
    Node node = mock(Node.class);
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    when(sessionProvider.getSession(null,repository)).thenReturn(session);
    when(session.getNodeByUUID(anyString())).thenReturn(node);
    // When
    String processedContent = imageProcessor.processImages(content, "nodeParent", null);

    // Then
    assertEquals(content, processedContent);
  }

  @Test
  public void shouldReturnUpdatedContentWhenEmbeddedImage() throws Exception {
    // Given
    HTMLUploadImageProcessorImpl imageProcessor = new HTMLUploadImageProcessorImpl(portalContainer, uploadService, repositoryService, linkManager, sessionProviderService,nodeHierarchyCreator,wcmService);
    String content = "<p>content with image: <img src=\"/portal/image?uploadId=123456\" /></p>";
    Node node = mock(Node.class);
    File imageFile = uploadFolder.newFile("image.png");
    UploadResource uploadImage = new UploadResource("123456", "image.png");
    uploadImage.setStoreLocation(imageFile.getPath());
    when(uploadService.getUploadResource(eq("123456"))).thenReturn(uploadImage);
    when(node.hasNode(eq("image.png"))).thenReturn(false);
    when(node.addNode(anyString(), anyString())).thenReturn(node);
    when(portalContainer.getName()).thenReturn("portal");
    when(portalContainer.getRestContextName()).thenReturn("rest");
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    when(repositoryEntry.getName()).thenReturn("repository");
    when(sessionProvider.getSession(null,repository)).thenReturn(session);
    when(node.getSession()).thenReturn(session);
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    when(session.getNodeByUUID(anyString())).thenReturn(node);
    when(workspace.getName()).thenReturn("collaboration");
    lenient().when(node.getPath()).thenReturn("/path/to/image.png");

    // When
    String processedContent = imageProcessor.processImages(content, "nodeParent", null);

    // Then
    assertTrue(processedContent.matches("<p>content with image: <img src=\"/portal/rest/images/repository/collaboration/[a-z0-9]+\" /></p>"));
  }
  @Test
  public void shouldReturnUpdatedContentWhenEmbeddedImageForSpace() throws Exception {
    // Given
    HTMLUploadImageProcessorImpl imageProcessor = new HTMLUploadImageProcessorImpl(portalContainer, uploadService, repositoryService, linkManager, sessionProviderService,nodeHierarchyCreator,wcmService);
    String content = "<p>content with image: <img src=\"/portal/image?uploadId=123456\" /></p>";
    Node node = mock(Node.class);
    File imageFile = uploadFolder.newFile("image.png");
    UploadResource uploadImage = new UploadResource("123456", "image.png");
    uploadImage.setStoreLocation(imageFile.getPath());
    when(uploadService.getUploadResource(eq("123456"))).thenReturn(uploadImage);
    when(node.hasNode(eq("image.png"))).thenReturn(false);
    when(node.addNode(anyString(), anyString())).thenReturn(node);
    when(portalContainer.getName()).thenReturn("portal");
    when(portalContainer.getRestContextName()).thenReturn("rest");
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    when(repositoryEntry.getName()).thenReturn("repository");
    when(sessionProvider.getSession("collaboration",repository)).thenReturn(session);
    when(node.getSession()).thenReturn(session);
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    when(session.getRootNode()).thenReturn(node);
    when(node.getNode(anyString())).thenReturn(node);
    when(workspace.getName()).thenReturn("collaboration");
    lenient().when(node.getPath()).thenReturn("/path/to/image.png");

    // When
    String processedContent = imageProcessor.processSpaceImages(content, "nodeParent", null);

    // Then
    assertTrue(processedContent.matches("<p>content with image: <img src=\"/portal/rest/images/repository/collaboration/[a-z0-9]+\" /></p>"));
  }

  @Test
  public void shouldReturnUpdatedContentWhenEmbeddedImportedImageForSpace() throws Exception {
    // Given
    HTMLUploadImageProcessorImpl imageProcessor = new HTMLUploadImageProcessorImpl(portalContainer, uploadService, repositoryService, linkManager, sessionProviderService,nodeHierarchyCreator,wcmService);
    String content = "<p>content with image: <img src=\"//-image.png-//\" /></p>";
    Node node = mock(Node.class);
    when(node.hasNode(eq("image.png"))).thenReturn(false);
    when(node.addNode(anyString(), anyString())).thenReturn(node);
    when(portalContainer.getName()).thenReturn("portal");
    when(portalContainer.getRestContextName()).thenReturn("rest");
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    when(repositoryEntry.getName()).thenReturn("repository");
    when(sessionProvider.getSession("collaboration",repository)).thenReturn(session);
    when(node.getSession()).thenReturn(session);
    when(node.getUUID()).thenReturn("123524");
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    when(session.getRootNode()).thenReturn(node);
    when(node.getNode(anyString())).thenReturn(node);
    when(workspace.getName()).thenReturn("collaboration");


    lenient().when(node.getPath()).thenReturn("/path/to/image.png");
    File file = new File(System.getProperty("java.io.tmpdir") + File.separator +"image.png");
    try (OutputStream outputStream = new FileOutputStream(file)) {
      IOUtils.copy(new ByteArrayInputStream("test data".getBytes()), outputStream);
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot create the file", e);
    }

    // When
    String processedContent = imageProcessor.processSpaceImages(content, "nodeParent", null);

    // Then
    assertTrue(processedContent.matches("<p>content with image: <img src=\"/portal/rest/images/repository/collaboration/[a-z0-9]+\" /></p>"));
  }

  @Test
  public void shouldReturnUpdatedContentWhenEmbeddedImageForUser() throws Exception {
    // Given
    HTMLUploadImageProcessorImpl imageProcessor = new HTMLUploadImageProcessorImpl(portalContainer, uploadService, repositoryService, linkManager, sessionProviderService,nodeHierarchyCreator,wcmService);
    String content = "<p>content with image: <img src=\"/portal/image?uploadId=123456\" /></p>";
    Node node = mock(Node.class);
    File imageFile = uploadFolder.newFile("image.png");
    UploadResource uploadImage = new UploadResource("123456", "image.png");
    uploadImage.setStoreLocation(imageFile.getPath());
    when(uploadService.getUploadResource(eq("123456"))).thenReturn(uploadImage);
    when(node.hasNode(eq("image.png"))).thenReturn(false);
    when(node.addNode(anyString(), anyString())).thenReturn(node);
    when(portalContainer.getName()).thenReturn("portal");
    when(portalContainer.getRestContextName()).thenReturn("rest");
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(sessionProviderService.getSystemSessionProvider(null)).thenReturn(sessionProvider);
    when(repositoryEntry.getName()).thenReturn("repository");
    when(nodeHierarchyCreator.getUserNode(sessionProvider, "userId")).thenReturn(node);
    when(node.getSession()).thenReturn(session);
    Workspace workspace = mock(Workspace.class);
    when(session.getWorkspace()).thenReturn(workspace);
    when(workspace.getName()).thenReturn("collaboration");
    lenient().when(node.getPath()).thenReturn("/path/to/image.png");

    // When
    String processedContent = imageProcessor.processUserImages(content, "userId", null);

    // Then
    assertTrue(processedContent.matches("<p>content with image: <img src=\"/portal/rest/images/repository/collaboration/[a-z0-9]+\" /></p>"));
  }


  @Test
  public void shouldReturnUpdatedContentForExport() throws Exception {
    // Given
    HTMLUploadImageProcessorImpl imageProcessor = new HTMLUploadImageProcessorImpl(portalContainer, uploadService, repositoryService, linkManager, sessionProviderService,nodeHierarchyCreator,wcmService);
    String content = "<p>content with image: <img src=\"/portal/rest/images/repository/collaboration/123456\" /></p>";
    Node node = mock(Node.class);
    Property property = mock(Property.class);
    when(portalContainer.getName()).thenReturn("portal");
    when(portalContainer.getRestContextName()).thenReturn("rest");
    when(repositoryService.getCurrentRepository()).thenReturn(repository);
    when(repository.getConfiguration()).thenReturn(repositoryEntry);
    when(repositoryEntry.getName()).thenReturn("repository");
    when(wcmService.getReferencedContent(anyObject(), anyString(),anyString() )).thenReturn(node);
    when(node.getNode(anyString())).thenReturn(node);
    when(node.getName()).thenReturn("image.png");
    when(node.getProperty(anyString())).thenReturn(property);
    when(property.getStream()).thenReturn(new ByteArrayInputStream("test data".getBytes()));

    // When
    String processedContent = imageProcessor.processImagesForExport(content);

    // Then
    assertTrue(processedContent.matches("<p>content with image: <img src=\"//-image.png-//\" /></p>"));
  }
}