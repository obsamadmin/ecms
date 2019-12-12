package org.exoplatform.ecm.webui.component.explorer.documents;

/**
 * The Class DocumentTemplate.
 */
public class DocumentTemplate {

  /** The extension. */
  protected String extension;

  /** The path. */
  protected String path;

  /** The label. */
  protected String label;

  /** The mime type. */
  protected String mimeType;

  /** The icon. */
  protected String icon;

  /**
   * Gets the path.
   *
   * @return the path
   */
  public String getPath() {
    return path;
  }

  /**
   * Sets the path.
   *
   * @param path the new path
   */
  public void setPath(String path) {
    this.path = path;
  }

  /**
   * Gets the label.
   *
   * @return the label
   */
  public String getLabel() {
    return label;
  }

  /**
   * Sets the label.
   *
   * @param label the new label
   */
  public void setLabel(String label) {
    this.label = label;
  }

  /**
   * Gets the mime type.
   *
   * @return the mime type
   */
  public String getMimeType() {
    return mimeType;
  }

  /**
   * Sets the mime type.
   *
   * @param mimeType the new mime type
   */
  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  /**
   * Gets the icon.
   *
   * @return the icon
   */
  public String getIcon() {
    return icon;
  }

  /**
   * Sets the icon.
   *
   * @param icon the new icon
   */
  public void setIcon(String icon) {
    this.icon = icon;
  }

  /**
   * Gets the extension.
   *
   * @return the extension
   */
  public String getExtension() {
    return extension;
  }

  /**
   * Sets the extension.
   *
   * @param extension the new extension
   */
  public void setExtension(String extension) {
    this.extension = extension;
  }

  @Override
  public String toString() {
    return "DocumentTemplate [extension=" + extension + ", path=" + path + ", label=" + label + ", mimeType=" + mimeType
        + ", icon=" + icon + "]";
  }

}
