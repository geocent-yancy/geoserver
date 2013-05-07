/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.rest.image;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.rest.AbstractCatalogListResource;
import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.restlet.Context;
import org.restlet.data.Request;
import org.restlet.data.Response;

public class ImageListResource extends AbstractCatalogListResource {

    public ImageListResource(Context context, Request request,
            Response response, Catalog catalog) {
        super(context, request, response, ImageFile.class, catalog);
    }

    @Override
    protected List handleListGet() throws Exception {
        String ws = getAttribute("workspace");
        String ds = getAttribute("datastore");
        String ft = getAttribute("featuretype");
        String fid = getAttribute("fid");
        String attr = getAttribute("attribute");
        
        // This method no longer works.
        throw new UnsupportedOperationException("ImageListResource is not currently implemented.");
        
        // Get a list of images for the Feature's Attribute
//        File directory = catalog.getResourceLoader().findOrCreateDirectory(ImageResource.getDirectoryPath(getRequest()));
//        
//        List<ImageFile> images = new ArrayList<ImageFile>();
//        
//        for (String filename : directory.list()) {
//            images.add(new ImageFile(filename));
//        }
//        
//        return images;
        // Get a list of Feature IDs
//        FeatureTypeInfo featureType;
//
//        featureType = catalog.getFeatureTypeByDataStore(catalog.getDataStoreByName(ws, ds), ft);
//        FeatureSource<? extends FeatureType, ? extends Feature> featureSource = featureType.getFeatureSource(null, null);
//
//        List featuresList = new ArrayList();
//        FeatureIterator<? extends Feature> features = featureSource.getFeatures().features();
//
//        while (features.hasNext()) {
//            Feature next = features.next();
//            featuresList.add(new ImageFile(next.getIdentifier().getID(), next));
//        }
//
//        return featuresList;
    }

    @Override
    public boolean allowPost() {
        return false;
    }

    @Override
    public boolean allowPut() {
        return false;
    }

    @Override
    public boolean allowDelete() {
        return false;
    }
}
