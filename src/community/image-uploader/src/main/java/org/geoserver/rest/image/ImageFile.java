package org.geoserver.rest.image;

/**
 *
 * @author Yancy
 */
public class ImageFile {

    private String id;

    public ImageFile(String i) {
        id = i;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }
}
