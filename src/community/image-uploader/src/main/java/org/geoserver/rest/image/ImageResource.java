package org.geoserver.rest.image;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.collections.SetUtils;
import org.apache.commons.io.FilenameUtils;
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
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.FidFilter;
import org.geotools.filter.FidFilterImpl;
import org.geotools.filter.FilterFactory;
import org.geotools.filter.FilterFactoryImpl;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.util.logging.Logging;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;
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

    /**
     * logger
     */
    static final Logger LOGGER = Logging.getLogger("org.geoserver.catalog.rest");
    GeoServer geoserver;
    Catalog catalog;
    FeatureTypeInfo featureTypeInfo;
    FilterFactory filterFactory;

    public ImageResource(Context context, Request request, Response response, GeoServer geoserver,
            FeatureTypeInfo featureType, FilterFactory filterFactory) {
        super(context, request, response);
        this.geoserver = geoserver;
        this.catalog = geoserver.getCatalog();
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
        String ds = getAttribute("datastore");
        String ft = getAttribute("featuretype");
        String fid = getAttribute("fid");
        String attr = getAttribute("attribute");
        String filename = getAttribute("file");
        String extension = getAttribute("format");

        String[] filePath = getFilePath(getRequest());
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
        return null != getAttribute("file");
    }

    @Override
    public void handlePost() {
        doUpload();
    }

    @Override
    public void handlePut() {
        doUpload();
    }

    /*
     * Choose the appropriate upload method
     */
    private void doUpload() {
        String method = (String) getRequest().getResourceRef().getLastSegment();
        if (method != null && method.toLowerCase().startsWith("file.")) {
            doFileUpload();
        } else if (method != null && method.toLowerCase().startsWith("url.")) {
//            doURLUpload;
        } else {
            final StringBuilder builder =
                    new StringBuilder("Unrecognized file upload method: ").append(method);
            throw new RestletException(builder.toString(), Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    /*
     * Add a new image file from the attachment to the request.
     */
    private void doFileUpload() {
        String ws = getAttribute("workspace");
        String ds = getAttribute("datastore");
        String ft = getAttribute("featuretype");
        String fid = getAttribute("fid");
        String attr = getAttribute("attribute");
        String ext = getAttribute("format");
        String[] directoryPath = getDirectoryPath(getRequest());
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
            RESTUtils.handleBinUpload(filename, directory, getRequest());
            setLink(filename);
            getResponse().setStatus(Status.SUCCESS_CREATED);
        } catch (IOException ex) {
            throw new RestletException("Could not write file " + filename,
                    Status.SERVER_ERROR_INTERNAL, ex);
        }
    }

    public void setLink(String filename) {
        String ws = getAttribute("workspace");
        String ds = getAttribute("datastore");
        String ft = getAttribute("featuretype");
        String fid = getAttribute("fid");
        String attr = getAttribute("attribute");
        String ext = getAttribute("format");

//        Set fids = new HashSet();
//        FeatureId featureId = new FeatureIdImpl(fid);
//        fids.add(featureId);
        //        Filter fidFilter = CommonFactoryFinder.getFilterFactory().id(SetUtils.EMPTY_SET);
//        FilterFactoryImpl filterFactoryImpl = new FilterFactoryImpl();
        FidFilter fidFilter = filterFactory.createFidFilter(fid);

        try {
            FeatureSource<? extends FeatureType, ? extends Feature> featureSource = featureTypeInfo.getFeatureSource(null, null);
//            FeatureCollection<? extends FeatureType, ? extends Feature> features =
//                    featureSource.getFeatures(fidFilter);
//            Feature next = features.features().next();
//            DataAccess<? extends FeatureType, ? extends Feature> dataStore = featureSource.getDataStore();
            Feature feature = getFeature(featureSource, fidFilter);
            Property attributeProperty = feature.getProperty(attr);

            if (featureSource instanceof FeatureStore) {
                // Just cast to get the methods we need
                FeatureStore<? extends FeatureType, ? extends Feature> featureStore =
                        (FeatureStore<? extends FeatureType, ? extends Feature>) featureSource;


//                FeatureType schema = featureStore.getSchema();
//                PropertyDescriptor descriptor = schema.getDescriptor(attr);
//                Name attrName = descriptor.getName();
                // Get the new list of image URLs to store
                String url = appendLinkToAttribute(attributeProperty, filename);

                // Actually modify the persisted data using the FID filter
                featureStore.modifyFeatures(attributeProperty.getName(), url, fidFilter);
            }

        } catch (IOException ex) {
            Logger.getLogger(ImageFinder.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    String appendLinkToAttribute(Property attributeProperty, String newFilename) {
        Object value = attributeProperty.getValue();

        if (null == value || value.toString().isEmpty()) {
            return getLink(newFilename);
        }

        return value + "," + getLink(newFilename);
    }

    /**
     * Create a URL that points the uploaded file.
     *
     * @param filename - the filename for the uploaded file
     * @return String - the full URL to the uploaded file
     */
    protected String getLink(String filename) {
//        String proxyBaseUrl = geoserver.getSettings().getProxyBaseUrl();
//        
//        if (null != proxyBaseUrl && !proxyBaseUrl.isEmpty()) {
//            // TODO: need full url
//            return proxyBaseUrl + filename;
//        }
        
        return getRequest().getResourceRef().getParentRef().toString() + filename;
    }

    /*
     * Assumes you'll only be getting one back.
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

        // We already checked that the size is one, so just return the first one.
        return featuresIterator.next();
    }

    public static String[] getFilePath(Request request) {
        String filename = RESTUtils.getAttribute(request, "file");
        String format = RESTUtils.getAttribute(request, "format");

        List<String> path = getDirectoryPathAsList(request);
        if (filename != null && format != null) {
            path.add(filename + "." + format);
        }
        return path.toArray(new String[]{});
    }

    public static String[] getDirectoryPath(Request request) {
        return getDirectoryPathAsList(request).toArray(new String[]{});
    }

    public static List<String> getDirectoryPathAsList(Request request) {
        String workspace = RESTUtils.getAttribute(request, "workspace");
        String datastore = RESTUtils.getAttribute(request, "datastore");
        String featureType = RESTUtils.getAttribute(request, "featuretype");
        String fid = RESTUtils.getAttribute(request, "fid");
        String attribute = RESTUtils.getAttribute(request, "attribute");

        List<String> path = new ArrayList<String>();
        path.add("workspaces");

        if (workspace != null) {
            path.add(workspace);
            if (datastore != null) {
                path.add(datastore);
                if (featureType != null) {
                    path.add(featureType);
                    path.add("images");
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
