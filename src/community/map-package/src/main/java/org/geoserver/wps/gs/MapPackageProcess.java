/* Copyright (c) 2001 - 2013 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wps.gs;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.opengis.wcs11.Wcs111Factory;
import org.geoserver.catalog.Catalog;
import org.geoserver.config.GeoServer;
import org.geoserver.wcs.WCSInfo;
import org.geoserver.wcs.WebCoverageService111;
import org.geoserver.wcs.kvp.GetCoverageRequestReader;
import org.geoserver.wfs.WFSInfo;
import org.geoserver.wps.WPSException;
import org.geoserver.wps.WPSStorageCleaner;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.process.gs.GSProcess;
import org.geotools.util.logging.Logging;

/**
 *
 * @author Yancy
 */
@DescribeProcess(title = "Map Package", description = "Creates a zip package containing the data for the layers in a given Web Map Context.")
public class MapPackageProcess implements GSProcess {
    static final Logger LOGGER = Logging.getLogger(MapPackageProcess.class);

    GeoServer geoserver;
    Catalog catalog;
    WPSStorageCleaner storage;
    
    WCSInfo wcs;
    WFSInfo wfs;

    public MapPackageProcess(GeoServer geoserver, Catalog catalog, WPSStorageCleaner storage) {
        this.geoserver = geoserver;
        this.catalog = catalog;
        this.storage = storage;
        
        this.wcs = geoserver.getService(WCSInfo.class);
        this.wfs = geoserver.getService(WFSInfo.class);
    }

    @DescribeResult(name = "zipLocation", description = "URL at which zipfile can be accessed")
    public URL execute(
            @DescribeParameter(name = "mapcontext", description = "Input Web Map Context xml") GridCoverage2D coverage)
            throws IOException, Exception {

        // Create a temporary directory to collect the datafiles
        File packageDirectory = new File(storage.getStorage(), "map-package-" + UUID.randomUUID().toString());
        if (!packageDirectory.mkdir()) {
           throw new WPSException("Unable to create temp directory for map-package " + packageDirectory.getCanonicalPath());
        }
        
        // Create a styles directory
        // Since right now we're just pulling data from this GeoServer, a common styles directory
        // makes sense. We only need one copy of a style. Pulling from multiple sources, we could
        // get multiple styles with the same name. We'll have to revisit this then.
        File stylesDirectory = new File(packageDirectory, "styles");
        if (!stylesDirectory.mkdir()) {
           throw new WPSException("Unable to create styles directory for layer");
        }
        
        // For each layer
        // create a directory
        File layer1Directory = new File(packageDirectory, "sfdem");
        if (!layer1Directory.mkdir()) {
           throw new WPSException("Unable to create directory for layer " + layer1Directory.getName());
        }
        
        // get the data (shapefile or geotiff)
        GetCoverageRequestReader getCoverageReader = new GetCoverageRequestReader(this.catalog);
        getCoverageReader.read(getCoverageReader.createRequest(), null, null);
        
        // get the style

        return null;
    }
}
