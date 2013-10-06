package org.opennms.features.vaadin.nodemaps.internal.gwt.client.event;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.discotools.gwt.leaflet.client.jsobject.JSObject;
import org.discotools.gwt.leaflet.client.jsobject.JSObjectWrapper;
import org.discotools.gwt.leaflet.client.types.LatLng;
import org.opennms.features.vaadin.nodemaps.internal.gwt.client.JSNodeMarker;
import org.opennms.features.vaadin.nodemaps.internal.gwt.client.SearchResults;
import org.opennms.features.vaadin.nodemaps.internal.gwt.client.ui.MarkerProvider;

public abstract class NodeMarkerSearchCallback extends JSObjectWrapper {
    Logger logger = Logger.getLogger(getClass().getName());

    private MarkerProvider m_markerProvider;

    protected NodeMarkerSearchCallback(final JSObject jsObject) {
        super(jsObject);
    }

    public NodeMarkerSearchCallback(final MarkerProvider provider) {
        super(JSObject.createJSFunction());
        setJSObject(getCallbackFunction());
        m_markerProvider = provider;
    }

    public abstract Collection<JSNodeMarker> search(final Collection<JSNodeMarker> markers, final String text);

    protected JSObject doSearch(final String text) {
        logger.log(Level.INFO, "doSearch(" + text +")");
        final Collection<JSNodeMarker> markers = search(m_markerProvider.getMarkers(), text);
        logger.log(Level.INFO, markers.size() + " markers returned.");
        final SearchResults results = SearchResults.create();
        for (final JSNodeMarker marker : markers) {
            final LatLng latLng = JSNodeMarker.coordinatesToLatLng(marker.getCoordinates());
            results.setProperty(marker.getNodeLabel(), latLng.getJSObject());
        }
        return results;
    }

    private native final JSObject getCallbackFunction() /*-{
        var self = this;
        return function(text) {
            return self.@org.opennms.features.vaadin.nodemaps.internal.gwt.client.event.NodeMarkerSearchCallback::doSearch(Ljava/lang/String;)(text);
        };
    }-*/;

}
