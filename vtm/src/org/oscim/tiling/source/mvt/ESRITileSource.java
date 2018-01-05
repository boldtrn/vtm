/*
 * Copyright 2013 Hannes Janetzek
 * Copyright 2016-2017 devemux86
 *
 * This file is part of the OpenScienceMap project (http://www.opensciencemap.org).
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.oscim.tiling.source.mvt;

import org.oscim.tiling.ITileDataSource;
import org.oscim.tiling.source.UrlTileDataSource;
import org.oscim.tiling.source.UrlTileSource;

public class ESRITileSource extends UrlTileSource {

    //private final static String DEFAULT_URL = "https://tile.thunderforest.com/thunderforest.outdoors-v1";
    //private final static String DEFAULT_PATH = "/{Z}/{X}/{Y}.vector.pbf";

    //private final static String DEFAULT_URL = "https://a.tiles.mapbox.com/v4/mapbox.mapbox-terrain-v2,mapbox.mapbox-streets-v7";
    //private final static String DEFAULT_PATH = "/{Z}/{X}/{Y}.vector.pbf";
    private final static String DEFAULT_URL = "https://basemaps.arcgis.com/arcgis/rest/services/World_Basemap_v2/VectorTileServer/tile";
    private final static String DEFAULT_PATH = "/{Z}/{X}/{Y}.pbf";

    public static class Builder<T extends Builder<T>> extends UrlTileSource.Builder<T> {
        private String locale = "";

        public Builder() {
            super(DEFAULT_URL, DEFAULT_PATH, 1, 17);
            //keyName("apikey");
            //keyName("access_token");
        }

        public T locale(String locale) {
            this.locale = locale;
            return self();
        }

        public ESRITileSource build() {
            return new ESRITileSource(this);
        }
    }

    @SuppressWarnings("rawtypes")
    public static Builder<?> builder() {
        return new Builder();
    }

    private final String locale;

    public ESRITileSource(Builder<?> builder) {
        super(builder);
        this.locale = builder.locale;
    }

    public ESRITileSource() {
        this(builder());
    }

    public ESRITileSource(String urlString) {
        this(builder().url(urlString));
    }

    @Override
    public ITileDataSource getDataSource() {
        return new UrlTileDataSource(this, new MvtTileDecoder(locale), getHttpEngine());
    }
}
