/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.rest.image;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.rest.AbstractCatalogFinder;
import org.geoserver.config.GeoServer;
import org.geoserver.rest.RestletException;
import org.geotools.filter.FilterFactory;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Resource;

public class ImageFinder extends AbstractCatalogFinder {

    GeoServer geoserver;
    FilterFactory filterFactory;

    public ImageFinder(GeoServer geoserver, Catalog catalog, FilterFactory filterFactory) {
        super(catalog);
        this.geoserver = geoserver;
        this.filterFactory = filterFactory;
    }

    @Override
    public Resource findTarget(Request request, Response response) {
        String ws = getAttribute(request, "workspace");
        String ft = getAttribute(request, "featuretype");
        String fid = getAttribute(request, "fid");
        String attr = getAttribute(request, "attribute");
        String filename = getAttribute(request, "file");

        //ensure referenced resources exist
        if (ws != null && catalog.getWorkspaceByName(ws) == null) {
            throw new RestletException("No such workspace: " + ws, Status.CLIENT_ERROR_NOT_FOUND);
        }

        FeatureTypeInfo featureTypeInfo = null;

        //look up by workspace/namespace
        NamespaceInfo ns = catalog.getNamespaceByPrefix(ws);
        if (ns == null || (featureTypeInfo = catalog.getFeatureTypeByName(ns, ft)) == null) {
            throw new RestletException("No such feature type: " + ws + "," + ft, Status.CLIENT_ERROR_NOT_FOUND);
        }

        // No fid, a list is requested
//        if (null == fid) {
        //check the list flag, if == 'available', just return the list 
        // of feature types available
//            Form form = request.getResourceRef().getQueryAsForm();
//            String list = form.getFirstValue( "list" );
//            if ("available".equalsIgnoreCase(list) || "available_with_geom".equalsIgnoreCase(list)) {
//                return new AvailableFeatureTypeResource(null,request,response,catalog);
//            }
//            if (request.getMethod() == Method.GET) {
//                return new ImageListResource(getContext(), request, response, catalog);
//            } else {
//                throw new RestletException("Can't do that on the list", Status.CLIENT_ERROR_METHOD_NOT_ALLOWED);
//            }
//        }

        return new ImageResource(getContext(), request, response, geoserver, catalog, featureTypeInfo, filterFactory);
    }
}
