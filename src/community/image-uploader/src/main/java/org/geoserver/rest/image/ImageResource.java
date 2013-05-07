package org.geoserver.rest.image;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.ArrayUtils;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.rest.AbstractResource;
import org.geoserver.rest.RestletException;
import org.geoserver.rest.format.DataFormat;
import org.geoserver.rest.format.MediaTypes;
import org.geoserver.rest.format.StringFormat;
import org.geoserver.rest.util.RESTUtils;
import org.geotools.data.FeatureSource;
import org.geotools.data.FeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.filter.FidFilter;
import org.geotools.filter.FilterFactory;
import org.geotools.util.logging.Logging;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.FileRepresentation;

/**
 * Provides a REST interface for uploading images to a featuretype. Stores the image in the data
 * directory and adds the URL to the column for the feature.
 *
 * @author Yancy
 */
public class ImageResource extends AbstractResource {

    static final Logger LOGGER = Logging.getLogger("org.geoserver.catalog.rest");
    GeoServer geoserver;
    Catalog catalog;
    FeatureTypeInfo featureTypeInfo;
    FilterFactory filterFactory;

    public ImageResource(Context context, Request request, Response response, GeoServer geoserver, Catalog catalog,
            FeatureTypeInfo featureType, FilterFactory filterFactory) {
        super(context, request, response);
        this.geoserver = geoserver;
//        this.catalog = geoserver.getCatalog();
        this.catalog = catalog;
        this.featureTypeInfo = featureType;
        this.filterFactory = filterFactory;
    }

    @Override
    protected List<DataFormat> createSupportedFormats(Request request, Response response) {
        List<DataFormat> formats = new ArrayList();
        formats.add(new StringFormat(MediaType.IMAGE_ALL));

        return formats;
    }

    @Override
    public void handleGet() {
        String ws = getAttribute("workspace");
        String ft = getAttribute("featuretype");
        String fid = getAttribute("fid");
        String attr = getAttribute("attribute");
        String filename = getAttribute("file");
        String extension = getAttribute("format");

        String[] filePath = getFilePath();
        File file = null;
        try {
            file = catalog.getResourceLoader().find(filePath);
        } catch (IOException ex) {
            String filePathString = ArrayUtils.toString(filePath);
            throw new RestletException("ResourceLoader is unable to load image " + filePathString,
                    Status.CLIENT_ERROR_NOT_FOUND, ex);
        }

        if (null == file || !file.exists()) {
            String filePathString = ArrayUtils.toString(filePath);
            throw new RestletException("Cannot find image " + filePathString,
                    Status.CLIENT_ERROR_NOT_FOUND);
        }

        if (!file.canRead()) {
            String filePathString = ArrayUtils.toString(filePath);
            throw new RestletException("Cannot read image " + filePathString,
                    Status.CLIENT_ERROR_UNPROCESSABLE_ENTITY);
        }

        // Get the MediaType from the file's extension
//        String extension = FilenameUtils.getExtension(file.getName());
        MediaType mediaType = MediaTypes.getMediaTypeForExtension(extension);
//            String mimeType = URLConnection.guessContentTypeFromName(file.getName());
//            MediaType mediaType = MediaType.valueOf(mimeType);

        // Output the Image to the response
        getResponse().setEntity(new FileRepresentation(file, mediaType, 300));
    }

    @Override
    public void handlePost() {
        doFileUpload();
    }

    @Override
    public void handlePut() {
        doFileUpload();
    }

    @Override
    public void handleDelete() {
        String filename = getAttribute("file");
        if (null == filename) {
            // delete all images for the feature's attribute
            doDirectoryDelete();
        } else {
            // delete the specified image
            doFileDelete();
        }
    }

    @Override
    public boolean allowGet() {
        return null != getAttribute("file");
    }

    @Override
    public boolean allowPost() {
        return null == getAttribute("file");
    }

    @Override
    public boolean allowPut() {
        return null == getAttribute("file");
    }

    @Override
    public boolean allowDelete() {
        return true;
    }

    /*
     * Add a new image file from the attachment to the request.
     */
    private void doFileUpload() {
        String ws = getAttribute("workspace");
        String ft = getAttribute("featuretype");
        String fid = getAttribute("fid");
        String attr = getAttribute("attribute");
        String[] directoryPath = getDirectoryPath();
        File directory = null;

        // Get the extension from the MediaType, which comes from the request's Content Type
        MediaType mediaType = getRequest().getEntity().getMediaType();
        String extension = mediaType.getSubType();
        // Theoretically this should work, but it returns null
//        String extension = MediaTypes.getExtensionForMediaType(mediaType);

        // Only allow images.
        if (!MediaType.IMAGE_ALL.includes(mediaType)) {
            throw new RestletException("Invalid mimetype: " + mediaType,
                    Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE);
        }

        try {
            // Get the parent directory to write the image to; create if necessary.
            directory = catalog.getResourceLoader().findOrCreateDirectory(directoryPath);
        } catch (IOException ex) {
            String directoryPathString = Arrays.toString(directoryPath);
            throw new RestletException("Could not find or create directory " + directoryPathString,
                    Status.SERVER_ERROR_INTERNAL, ex);
        }

        if (null == directory || !directory.canWrite()) {
            String directoryPathString = Arrays.toString(directoryPath);
            throw new RestletException("Invalid directory " + directoryPathString,
                    Status.SERVER_ERROR_INTERNAL);
        }

        // Create a unique name for the uploaded image
        String filename = UUID.randomUUID() + "." + extension;

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "POST file: mimetype={0}, path={1}, file={2}",
                    new Object[]{mediaType, directory.getAbsolutePath(), filename});
        }

        try {
            // Write the file to disk.
            // In version >OpenGeo suite's on 4/24/2013 use false as the 3rd parameter.
//            RESTUtils.handleBinUpload(filename, directory, false, getRequest());
            RESTUtils.handleBinUpload(filename, directory, getRequest());
            setLink(filename, true);
            getResponse().setStatus(Status.SUCCESS_CREATED);
        } catch (IOException ex) {
            throw new RestletException("Could not write file " + filename,
                    Status.SERVER_ERROR_INTERNAL, ex);
        }
    }

    protected void doDirectoryDelete() {
        // Delete the directory from the file system
        String[] directoryPath = getDirectoryPath();
        try {
            File directory = catalog.getResourceLoader().find(directoryPath);

            if (null != directory && directory.exists() && directory.isDirectory()) {
                // Got to delete all the files before you can delete the directory.
                for (File file : directory.listFiles()) {
                    file.delete();
                }

                boolean wasDeleted = directory.delete();
                LOGGER.log(Level.INFO, "REST attachment uploader deleted directory {0}, success? {1}",
                        new Object[]{directory.getCanonicalPath(), wasDeleted});
            }
        } catch (IOException ex) {
            throw new RestletException("Error occurred while finding the directory to delete.",
                    Status.SERVER_ERROR_INTERNAL, ex);
        }

        // Delete the links from the feature
        try {
            FeatureSource<? extends FeatureType, ? extends Feature> featureSource =
                    featureTypeInfo.getFeatureSource(null, null);
            if (featureSource instanceof FeatureStore) {
                String fid = getAttribute("fid");
                String attr = getAttribute("attribute");
                FidFilter fidFilter = filterFactory.createFidFilter(fid);
                Feature feature = getFeature(featureSource, fidFilter);
                Property attributeProperty = feature.getProperty(attr);

                // Just cast to get the methods we need
                FeatureStore<? extends FeatureType, ? extends Feature> featureStore =
                        (FeatureStore<? extends FeatureType, ? extends Feature>) featureSource;

                // Actually modify the persisted data using the FID filter
                featureStore.modifyFeatures(attributeProperty.getName(), null, fidFilter);
                LOGGER.log(Level.INFO, "REST attachment uploader removed links from feature {0}",
                        feature.getIdentifier());
            }
        } catch (IOException ex) {
            throw new RestletException("Error occurred while finding the feature to remove links from.",
                    Status.SERVER_ERROR_INTERNAL, ex);
        }
    }

    protected void doFileDelete() {
        String[] filePath = getFilePath();

        try {
            File file = catalog.getResourceLoader().find(filePath);

            if (null != file && file.exists() && file.isFile()) {

                // Delete the file from the file system
                boolean wasDeleted = file.delete();
                LOGGER.log(Level.INFO, "REST attachment uploader deleted file {0}, success? {1}",
                        new Object[]{file.getCanonicalPath(), wasDeleted});

                // Delete the link from the feature
                setLink(file.getName(), false);
            }
        } catch (IOException ex) {
            throw new RestletException("Error occurred while finding the file to delete.",
                    Status.SERVER_ERROR_INTERNAL, ex);
        }

    }

    /**
     * Set and commit the link to the Feature's Attribute
     *
     * @param filename
     * @param append - hack to choose whether to append or remove
     */
    protected void setLink(String filename, boolean append) {
        String ws = getAttribute("workspace");
        String ft = getAttribute("featuretype");
        String fid = getAttribute("fid");
        String attr = getAttribute("attribute");

        try {
            FeatureSource<? extends FeatureType, ? extends Feature> featureSource =
                    featureTypeInfo.getFeatureSource(null, null);

            if (featureSource instanceof FeatureStore) {
                FidFilter fidFilter = filterFactory.createFidFilter(fid);
                Feature feature = getFeature(featureSource, fidFilter);
                Property attributeProperty = feature.getProperty(attr);

                // Just cast to get the methods we need
                FeatureStore<? extends FeatureType, ? extends Feature> featureStore =
                        (FeatureStore<? extends FeatureType, ? extends Feature>) featureSource;

                // Get the new list of image URLs to store
                String url;
                if (append) {
                    url = appendLinkToAttribute(attributeProperty, filename);
                } else {
                    url = removeLinkFromAttribute(attributeProperty, filename);
                }

                // Actually modify the persisted data using the FID filter
                featureStore.modifyFeatures(attributeProperty.getName(), url, fidFilter);
            }
        } catch (IOException ex) {
            throw new RestletException("Error occurred while finding the feature to set the link on.",
                    Status.SERVER_ERROR_INTERNAL, ex);
        }
    }

    String appendLinkToAttribute(Property attributeProperty, String newFilename) {
        Object value = attributeProperty.getValue();

        if (null == value || value.toString().isEmpty()) {
            return getLink(newFilename);
        }

        return value + "," + getLink(newFilename);
    }

    String removeLinkFromAttribute(Property attributeProperty, String newFilename) {
        Object value = attributeProperty.getValue();
        String link = getLink(newFilename);

        if (null == value || value.toString().isEmpty()) {
            return null;
        }

        // If the link is in the middle of the list, remove it and the comma
        String t = value.toString().replace(link + ",", "");
        // If the link is at the end of the list, remove it
        t = t.replace(link, "");

        // If the link now ends in a comma, remove the trailing comma
        if (t.endsWith(",")) {
            return t.substring(0, t.length() - 1);
        }

        return t;
    }

    /**
     * Create a URL that points the uploaded file.
     *
     * @param filename - the filename for the uploaded file
     * @return String - the full URL to the uploaded file
     */
    protected String getLink(String filename) {
        String link;
        String proxyBaseUrl = geoserver.getSettings().getProxyBaseUrl();

        if (null != proxyBaseUrl && !proxyBaseUrl.isEmpty()) {
            // Use the Proxy Base URL in Global Settings if it exists
            String ws = getAttribute("workspace");
            String ft = getAttribute("featuretype");
            String fid = getAttribute("fid");
            String attr = getAttribute("attribute");
            link = proxyBaseUrl + needsSlash(proxyBaseUrl) + "rest/attachments/" + ws + "/" + ft
                    + "/" + fid + "/" + attr + "/" + filename;
        } else {
            // Otherwise use the URL from the request
            String requestRef = getRequest().getResourceRef().toString();
            link = requestRef + needsSlash(requestRef) + filename;
        }

        return link;
    }

    /**
     * This returns a slash(/) if needed, empty string if not.
     *
     * @param link
     * @return
     */
    public String needsSlash(String link) {
        if (null != link && link.endsWith("/")) {
            return "";
        } else {
            return "/";
        }
    }

    /*
     * Assumes your Filter will only return one Feature.
     */
    Feature getFeature(FeatureSource<? extends FeatureType, ? extends Feature> featureSource,
            Filter filter) throws IOException {
        // Get the FeatureCollection from the FeatureSource using the Filter
        FeatureCollection<? extends FeatureType, ? extends Feature> features =
                featureSource.getFeatures(filter);

        if (features.size() != 1) {
            throw new RestletException("Filter did not return exactly 1 feature",
                    Status.SERVER_ERROR_INTERNAL);
        }

        // Get the Iterator
        FeatureIterator<? extends Feature> featuresIterator = features.features();

        // We already checked that the size is exactly one, so just return the first one.
        Feature feature = featuresIterator.next();
        // GeoTools throws a warning if you don't close it!
        featuresIterator.close();

        return feature;
    }

    public String[] getFilePath() {
        String filename = getAttribute("file");
        String format = getAttribute("format");

        List<String> path = getDirectoryPathAsList();
        if (filename != null && format != null) {
            path.add(filename + "." + format);
        }

        return path.toArray(new String[]{});
    }

    public String[] getDirectoryPath() {
        return getDirectoryPathAsList().toArray(new String[]{});
    }

    public List<String> getDirectoryPathAsList() {
        String workspace = getAttribute("workspace");
        String datastore = featureTypeInfo.getStore().getName();
        String featureType = getAttribute("featuretype");
        String fid = getAttribute("fid");
        String attribute = getAttribute("attribute");

        List<String> path = new ArrayList<String>();
        path.add("workspaces");

        if (workspace != null) {
            path.add(workspace);
            if (datastore != null) {
                path.add(datastore);
                if (featureType != null) {
                    path.add(featureType);
                    path.add("attachments");
                    if (fid != null) {
                        path.add(fid);
                        if (attribute != null) {
                            path.add(attribute);
                        }
                    }
                }
            }
        }

        return path;
    }
}
