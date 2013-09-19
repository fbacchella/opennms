/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.rrd.rrd4j;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.opennms.netmgt.rrd.RrdDataSource;
import org.opennms.netmgt.rrd.RrdGraphDetails;
import org.opennms.netmgt.rrd.RrdStrategy;
import org.opennms.netmgt.rrd.RrdUtils;
import org.rrd4j.ConsolFun;
import org.rrd4j.DsType;
import org.rrd4j.core.FetchData;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdDef;
import org.rrd4j.core.Sample;
import org.rrd4j.core.timespec.TimeParser;
import org.rrd4j.data.Variable;
import org.rrd4j.graph.RrdGraph;
import org.rrd4j.graph.RrdGraphConstants;
import org.rrd4j.graph.RrdGraphConstants.FontTag;
import org.rrd4j.graph.RrdGraphDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Provides a RRD4J based implementation of RrdStrategy. It uses RRD4J 2.3 in
 * FILE mode (NIO is too memory consuming for the large number of files that we
 * open)
 *
 * @author ranger
 * @author Fabrice Bacchella
 */
public class Rrd4JRrdStrategy implements RrdStrategy<RrdDef,RrdDb> {
    private static final Logger LOG = LoggerFactory.getLogger(Rrd4JRrdStrategy.class);
    private static final String BACKEND_FACTORY_PROPERTY = "org.rrd4j.core.RrdBackendFactory";
    private static final String DEFAULT_BACKEND_FACTORY = "FILE";
    private static final String VERSION_PROPERTY = "org.rrd4j.core.RrdVersion";
    // The default RRD version, should be 2 for faster IO, but not compatible with jrobin
    private static final int DEFAULT_VERSION = 1;
    private static final Set<String> COLORNAMES = new HashSet<String>(RrdGraphConstants.COLOR_NAMES.length);
    private static final String FLOATINGPOINTPATTERN = "[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?";  // see http://www.regular-expressions.info/floatingpoint.html, removed the [+-]?
    private static final Pattern LINEPATTERN = Pattern.compile("LINE(" + FLOATINGPOINTPATTERN + "):");
    // A set of fonts know by the VM
    private static final Set<String> FONTS = new HashSet<String>();
    // Map VDEF string to variable class that will be instanciated
    private static final Map<String, Class< ? extends Variable>> VDEFOPERATORS = new HashMap<String, Class< ? extends Variable>>();
    // A default stroke, used to extract cap, join and miterlimit default values
    private static final BasicStroke DEFAULTSTROKE = new BasicStroke();

    static {
        // Fill with the color list from RRD4J
        COLORNAMES.addAll(Arrays.asList(RrdGraphConstants.COLOR_NAMES));
        
        // Fill with the know fonts
        FONTS.addAll(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        
        // Fill the variables names
        VDEFOPERATORS.put("MAXIMUM", Variable.MAX.class);
        VDEFOPERATORS.put("MINIMUM", Variable.MIN.class);
        VDEFOPERATORS.put("AVERAGE", Variable.AVERAGE.class);
        VDEFOPERATORS.put("STDEV", Variable.STDDEV.class);
        VDEFOPERATORS.put("LAST", Variable.LAST.class);
        VDEFOPERATORS.put("FIRST", Variable.FIRST.class);
        VDEFOPERATORS.put("TOTAL", Variable.TOTAL.class);
        VDEFOPERATORS.put("LSLSLOPE", Variable.LSLSLOPE.class);
        VDEFOPERATORS.put("LSLINT", Variable.LSLINT.class);
        VDEFOPERATORS.put("LSLCORREL", Variable.LSLCORREL.class);
    }

    final class GraphDefInformations {
        String type;
        String name;
        String[] args;
        Map<String, String> opts;
    }

    /*
     * Ensure that we only initialize certain things *once* per
     * Java VM, not once per instantiation of this class.
     */
    private static boolean s_initialized = false;

    private Properties m_configurationProperties;

    /**
     * <p>getConfigurationProperties</p>
     *
     * @return a {@link java.util.Properties} object.
     */
    public Properties getConfigurationProperties() {
        return m_configurationProperties;
    }

    /** {@inheritDoc} */
    @Override
    public void setConfigurationProperties(final Properties configurationParameters) {
        m_configurationProperties = configurationParameters;
        if(!s_initialized) {
            String factory = null;
            if (m_configurationProperties == null) {
                factory = DEFAULT_BACKEND_FACTORY;
            } else {
                factory = (String)m_configurationProperties.get(BACKEND_FACTORY_PROPERTY);
            }
            try {
                RrdDb.setDefaultFactory(factory);
                s_initialized=true;
            } catch (RuntimeException e) {
                LOG.error("Could not set default RRD4J RRD factory", e);
            }
        }
    }

    /**
     * Closes the RRD4J RrdDb.
     *
     * @param rrdFile a {@link org.rrd4j.core.RrdDb} object.
     * @throws java.lang.Exception if any.
     */
    @Override
    public void closeFile(final RrdDb rrdFile) throws Exception {
        rrdFile.close();
    }

    /** {@inheritDoc} */
    @Override
    public RrdDef createDefinition(final String creator,
            final String directory, final String rrdName, int step,
            final List<RrdDataSource> dataSources, final List<String> rraList) throws Exception {
        File f = new File(directory);
        f.mkdirs();

        String fileName = directory + File.separator + rrdName + RrdUtils.getExtension();

        if (new File(fileName).exists()) {
            LOG.debug("createDefinition: filename [{}] already exists returning null as definition", fileName);
            return null;
        }

        RrdDef def = new RrdDef(fileName);

        int version;
        if (m_configurationProperties != null) {
            String versionStr =  m_configurationProperties.getProperty(VERSION_PROPERTY);
            version = stringToType(versionStr, DEFAULT_VERSION);
        } else {
            version = DEFAULT_VERSION;
        }

        def.setVersion(version);

        def.setStartTime(1000);
        def.setStep(step);

        for (RrdDataSource dataSource : dataSources) {
            String dsMin = dataSource.getMin();
            String dsMax = dataSource.getMax();
            double min = (dsMin == null || "U".equals(dsMin) ? Double.NaN : Double.parseDouble(dsMin));
            double max = (dsMax == null || "U".equals(dsMax) ? Double.NaN : Double.parseDouble(dsMax));
            def.addDatasource(dataSource.getName(), DsType.valueOf(dataSource.getType()), dataSource.getHeartBeat(), min, max);
        }

        for (String rra : rraList) {
            def.addArchive(rra);
        }

        return def;
    }

    /**
     * Creates the RRD4J RrdDb from the def by opening the file and then
     * closing it.
     *
     * @param rrdDef a {@link org.rrd4j.core.RrdDef} object.
     * @throws java.lang.Exception if any.
     */
    @Override
    public void createFile(final RrdDef rrdDef,  Map<String, String> attributeMappings) throws Exception {
        if (rrdDef == null) {
            LOG.debug("createRRD: skipping RRD file");
            return;
        }
        LOG.info("createRRD: creating RRD file {}", rrdDef.getPath());

        RrdDb rrd = new RrdDb(rrdDef);
        rrd.close();

        String filenameWithoutExtension = rrdDef.getPath().replace(RrdUtils.getExtension(), "");
        int lastIndexOfSeparator = filenameWithoutExtension.lastIndexOf(File.separator);

        RrdUtils.createMetaDataFile(
                filenameWithoutExtension.substring(0, lastIndexOfSeparator),
                filenameWithoutExtension.substring(lastIndexOfSeparator),
                attributeMappings
                );
    }

    /**
     * {@inheritDoc}
     *
     * Opens the RRD4J RrdDb by name and returns it.
     */
    @Override
    public RrdDb openFile(final String fileName) throws Exception {
        RrdDb rrd = new RrdDb(fileName);
        return rrd;
    }

    /**
     * {@inheritDoc}
     *
     * Creates a sample from the RRD4J RrdDb and passes in the data provided.
     */
    @Override
    public void updateFile(final RrdDb rrdFile, final String owner, final String data) throws Exception {
        Sample sample = rrdFile.createSample();
        sample.setAndUpdate(data);
    }

    /**
     * Initialized the RrdDb to use the FILE factory because the NIO factory
     * uses too much memory for our implementation.
     *
     * @throws java.lang.Exception if any.
     */
    public Rrd4JRrdStrategy() throws Exception {
        String home = System.getProperty("opennms.home");
        System.setProperty("jrobin.fontdir", home + File.separator + "etc");
    }

    /**
     * {@inheritDoc}
     *
     * Fetch the last value from the RRD4J RrdDb file.
     */
    @Override
    public Double fetchLastValue(final String fileName, final String ds, final int interval) throws NumberFormatException, org.opennms.netmgt.rrd.RrdException {
        return fetchLastValue(fileName, ds, "AVERAGE", interval);
    }

    /** {@inheritDoc} */
    @Override
    public Double fetchLastValue(final String fileName, final String ds, final String consolidationFunction, final int interval)
            throws org.opennms.netmgt.rrd.RrdException {
        RrdDb rrd = null;
        try {
            long now = System.currentTimeMillis();
            long collectTime = (now - (now % interval)) / 1000L;
            rrd = new RrdDb(fileName, true);
            FetchData data = rrd.createFetchRequest(ConsolFun.valueOf(consolidationFunction), collectTime, collectTime).fetchData();
            LOG.debug(data.toString());
            double[] vals = data.getValues(ds);
            if (vals.length > 0) {
                return new Double(vals[vals.length - 1]);
            }
            return null;
        } catch (IOException e) {
            throw new org.opennms.netmgt.rrd.RrdException("Exception occurred fetching data from " + fileName, e);
        } catch (RuntimeException e) {
            throw new org.opennms.netmgt.rrd.RrdException("Exception occurred fetching data from " + fileName, e);
        } finally {
            if (rrd != null) {
                try {
                    rrd.close();
                } catch (IOException e) {
                    LOG.error("Failed to close rrd file: {}", fileName, e);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public Double fetchLastValueInRange(final String fileName, final String ds, final int interval, final int range) throws NumberFormatException, org.opennms.netmgt.rrd.RrdException {
        RrdDb rrd = null;
        try {
            rrd = new RrdDb(fileName, true);
            long now = System.currentTimeMillis();
            long latestUpdateTime = (now - (now % interval)) / 1000L;
            long earliestUpdateTime = ((now - (now % interval)) - range) / 1000L;
            LOG.debug("fetchInRange: fetching data from {} to {}", earliestUpdateTime, latestUpdateTime);

            FetchData data = rrd.createFetchRequest(ConsolFun.AVERAGE, earliestUpdateTime, latestUpdateTime).fetchData();

            double[] vals = data.getValues(ds);
            long[] times = data.getTimestamps();

            // step backwards through the array of values until we get something that's a number
            for(int i = vals.length - 1; i >= 0; i--) {
                if ( Double.isNaN(vals[i]) ) {
                    LOG.debug("fetchInRange: Got a NaN value at interval: {} continuing back in time", times[i]);
                } else {
                    LOG.debug("Got a non NaN value at interval: {} : {}", times[i], vals[i] );
                    return new Double(vals[i]);
                }
            }
            return null;
        } catch (IOException e) {
            throw new org.opennms.netmgt.rrd.RrdException("Exception occurred fetching data from " + fileName, e);
        } catch (RuntimeException e) {
            throw new org.opennms.netmgt.rrd.RrdException("Exception occurred fetching data from " + fileName, e);
        } finally {
            if (rrd != null) {
                try {
                    rrd.close();
                } catch (IOException e) {
                    LOG.error("Failed to close rrd file: {}", fileName, e);
                }
            }
        }
    }

    private Color getColor(final String colorValue) {
        int rVal = Integer.parseInt(colorValue.substring(0, 2), 16);
        int gVal = Integer.parseInt(colorValue.substring(2, 4), 16);
        int bVal = Integer.parseInt(colorValue.substring(4, 6), 16);
        if (colorValue.length() == 6) {
            return new Color(rVal, gVal, bVal);
        }

        int aVal = Integer.parseInt(colorValue.substring(6, 8), 16);
        return new Color(rVal, gVal, bVal, aVal);
    }

    // For compatibility with RRDtool defs, the colour value for
    // LINE and AREA is optional. If it's missing, the line is rendered
    // invisibly.
    private Color getColorOrInvisible(final String[] array, final int index) {
        if (array.length > index) {
            return getColor(array[index]);
        }
        return RrdGraphConstants.BLIND_COLOR;
    }

    /** {@inheritDoc} */
    @Override
    public InputStream createGraph(final String command, final File workDir) throws IOException, org.opennms.netmgt.rrd.RrdException {
        return createGraphReturnDetails(command, workDir).getInputStream();
    }

    /**
     * {@inheritDoc}
     *
     * This constructs a graphDef by parsing the rrdtool style command and using
     * the values to create the RRD4J graphDef. It does not understand the 'AT
     * style' time arguments however. Also there may be some rrdtool parameters
     * that it does not understand. These will be ignored. The graphDef will be
     * used to construct an RrdGraph and a PNG image will be created. An input
     * stream returning the bytes of the PNG image is returned.
     */
    @Override
    public RrdGraphDetails createGraphReturnDetails(final String command, final File workDir) throws IOException, org.opennms.netmgt.rrd.RrdException {

        try {
            String[] commandArray = tokenize(command, " \t", false);

            RrdGraphDef graphDef = createGraphDef(workDir, commandArray);
            graphDef.setSignature("OpenNMS/RRD4J");

            RrdGraph graph = new RrdGraph(graphDef);

            /*
             * We use a custom RrdGraphDetails object here instead of the
             * DefaultRrdGraphDetails because we won't have an InputStream
             * available if no graphing commands were used, e.g.: if we only
             * use PRINT or if the user goofs up a graph definition.
             *
             * We want to throw an RrdException if the caller calls
             * RrdGraphDetails.getInputStream and no graphing commands were
             * used.  If they just call RrdGraphDetails.getPrintLines, though,
             * we don't want to throw an exception.
             */
            return new Rrd4JRrdGraphDetails(graph, command);
        } catch (Throwable e) {
            LOG.error("RRD4J: exception occurred creating graph", e);
            throw new org.opennms.netmgt.rrd.RrdException("An exception occurred creating the graph: " + e.getMessage(), e);
        }
    }

    /** {@inheritDoc} 
     * Does nothing, this strategy doesn't queue.
     * */
    @Override
    public void promoteEnqueuedFiles(Collection<String> rrdFiles) {
        // no need to do anything since this strategy doesn't queue
    }

    @SuppressWarnings("null")
    private <ArgClass> ArgClass stringToType(String arg, ArgClass defaultVal) {
        try {
            @SuppressWarnings("unchecked")
            Class<ArgClass> clazz = (Class<ArgClass>) defaultVal.getClass();
            Constructor<ArgClass> c = clazz.getConstructor(String.class);
            ArgClass n = c.newInstance(arg);
            return n;
        } catch (Exception e) {
            if(defaultVal != null) {
                return defaultVal;

            } else {
                throw new IllegalArgumentException("can't parse " + arg + " to " + defaultVal.getClass().getSimpleName());
            }
        }        
    }

    private <ArgClass> ArgClass parseOptType(String arg, String paramaName, Iterator<String> i, ArgClass defaultVal, String error) {
        if(defaultVal != null) {
            String paramtry = String.format("--%s=", paramaName);
            String paramStringValue = null;
            if (arg.startsWith(paramtry)) {
                paramStringValue =  arg.substring(paramtry.length());
            } else if (arg.equals("--" + paramaName)) {
                if(i.hasNext()) {
                    paramStringValue = i.next();
                }
            }
            if(paramStringValue == null) {
                throw new IllegalArgumentException(error);
            }
            return stringToType(paramStringValue, defaultVal);
        }
        else {
            if(defaultVal instanceof Boolean && arg.equals("--" + paramaName)) {
                @SuppressWarnings("unchecked")
                ArgClass n = (ArgClass) Boolean.TRUE;
                return n;
            }
            else {
                throw new IllegalArgumentException(error);                
            }
        }
    }

    String nextArg(String arg) {
        if(! arg.startsWith("--")) {
            return null;
        }
        int equals  = arg.indexOf('=');
        if(equals > 2) {
            arg = arg.substring(2, equals);
        }
        else {
            arg = arg.substring(2);
        }
        return arg;
    }

    /**
     * Parse a definition element.
     * 
     * It expect a string formated as: <p>
     * type:[name=]arg1[:arg]+[:option[=.]*]*<p>
     * It fills a {@link GraphDefInformations} object with
     * <pre>
     * GraphDefInformations.type = type
     * GraphDefInformations.name = a optional name extracted form arg1
     * GraphDefInformations.args = a String[] of the mandatory args
     * GraphDefInformations.opts = a Map of the options and their values 
     * </pre>
     * @param line The line to parse
     * @param countArgs the expected number or arguments, all other elements will be options
     * @param isData if true, arg0 is name=arg0
     * @return information parsed from the definition line
     */
    GraphDefInformations parseGraphDefElement(String line, int countArgs, boolean isData) {
        String[] token = tokenize(line, ":", true);
        GraphDefInformations info = new GraphDefInformations();
        info.type = token[0];
        info.name = null;
        info.opts = new HashMap<String, String>();
        info.args = new String[countArgs];
        if(isData) {
            String[] nametokens = token[1].split("=");
            info.name = nametokens[0];
            info.args[0] = nametokens[1];
        }
        else {
            info.args[0] = token[1];
        }
        for(int i = 1 ; i < countArgs ; i++) {
            if( i  + 1 < token.length) {
                info.args[i] = token[i + 1];
            }
            else {
                info.args[i] = null;
            }
        }

        for(int i = countArgs + 1; i < token.length; i++) {
            String optline = token[i];
            String[] opttokens = optline.split("=");
            String key = opttokens[0];
            String value = opttokens.length == 2 ? opttokens[1] : null;
            info.opts.put(key, value);
        }
        return info;
    }

    /**
     * <p>createGraphDef</p>
     *
     * @param workDir a {@link java.io.File} object.
     * @param inputArray an array of {@link java.lang.String} objects.
     * @return a {@link org.rrd4j.graph.RrdGraphDef} object.
     */
    protected RrdGraphDef createGraphDef(final File workDir, final String[] inputArray) {
        RrdGraphDef graphDef = new RrdGraphDef();
        graphDef.setImageFormat("PNG");
        long start = 0;
        long end = 0;
        long step = 0;

        int height = 100;
        int width = 400;
        double lowerLimit = Double.NaN;
        double upperLimit = Double.NaN;
        boolean rigid = false;
        Map<String,List<String>> defs = new LinkedHashMap<String,List<String>>();

        final String[] commandArray;
        if (inputArray[0].contains("rrdtool") && inputArray[1].equals("graph") && inputArray[2].equals("-")) {
            commandArray = Arrays.copyOfRange(inputArray, 3, inputArray.length);
        } else {
            commandArray = inputArray;
        }

        LOG.debug("command array = {}", Arrays.toString(commandArray));
        for(Iterator<String> i =  Arrays.asList(commandArray).iterator(); i.hasNext(); ) {
            String arg = i.next();
            String optName = nextArg(arg);
            LOG.debug("arg = {}", arg);
            LOG.debug("opt name = {}", optName);

            if("start".equals(optName)) {
                start = parseOptType(arg, optName, i, new Long(0), "").longValue();
            }
            else if("end".equals(optName)) {
                end = parseOptType(arg, optName, i, new Long(0), "").longValue();
            }
            else if("step".equals(optName)) {
                step = parseOptType(arg, optName, i, new Long(0), "").longValue();
                graphDef.setStep(step);
            }
            else if("title".equals(optName)) {
                String title = parseOptType(arg, optName, i, "", "");
                graphDef.setTitle(title);
            }
            else if("color".equals(optName)) {
                String color = parseOptType(arg, optName, i, "", "--color must be followed by a color");
                parseGraphColor(graphDef, color);
            }
            else if("vertical-label".equals(optName)) {
                String vlabel = parseOptType(arg, optName, i, "", "--vertical-label must be followed by a label");
                graphDef.setVerticalLabel(vlabel);
            }
            else if("height".equals(optName)) {
                height = parseOptType(arg, optName, i, new Integer(0), "--height must be followed by a number").intValue();
            }
            else if("width".equals(optName)) {
                int exponent = parseOptType(arg, optName, i, new Integer(0), "--units-exponent must be followed by a number").intValue();
                graphDef.setUnitsExponent(exponent);
            }
            else if("lower-limit".equals(optName)) {
                lowerLimit = parseOptType(arg, optName, i, new Double(0), "--lower-limit must be followed by a number").doubleValue();
            }
            else if("upper-limit".equals(optName)) {
                upperLimit = parseOptType(arg, optName, i, new Double(0), "--upper-limit must be followed by a number").doubleValue();
            }
            else if("base".equals(optName)) {
                double base = parseOptType(arg, optName, i, new Double(0), "--base must be followed by a number").doubleValue();
                graphDef.setBase(base);
            }
            else if("logarithmic".equals(optName)) {
                boolean logarithmic = parseOptType(arg, optName, i, Boolean.FALSE, "").booleanValue();
                graphDef.setLogarithmic(logarithmic);
            }
            else if("font".equals(optName)) {
                String font = parseOptType(arg, optName, i, "", "--font must be followed by an argument");
                processRrdFontArgument(graphDef, font);
            }
            else if("imgformat".equals(optName)) {
                String imgformat = parseOptType(arg, optName, i, "", "--imgformat must be followed by an argument");
                graphDef.setImageFormat(imgformat);
            }
            else if("lazy".equals(optName)) {
                boolean lazy = parseOptType(arg, optName, i, Boolean.FALSE, "").booleanValue();
                graphDef.setLazy(lazy);
            }
            else if("rigid".equals(optName)) {
                rigid = parseOptType(arg, optName, i, Boolean.FALSE, "").booleanValue();
            }
            else if("no-legend".equals(optName)) {
                boolean noLegend = parseOptType(arg, optName, i, Boolean.FALSE, "").booleanValue();
                graphDef.setNoLegend(noLegend);
            }
            else if("alt-autoscale".equals(optName)) {
                boolean altAutoscale = parseOptType(arg, optName, i, Boolean.FALSE, "").booleanValue();
                graphDef.setAltAutoscale(altAutoscale);
            }
            else if("alt-autoscale-max".equals(optName)) {
                boolean altAutoscaleMax = parseOptType(arg, optName, i, Boolean.FALSE, "").booleanValue();
                graphDef.setAltAutoscaleMax(altAutoscaleMax);
            }
            else if("alt-autoscale-min".equals(optName)) {
                boolean altAutoscaleMin = parseOptType(arg, optName, i, Boolean.FALSE, "").booleanValue();
                graphDef.setAltAutoscaleMax(altAutoscaleMin);
            }
            else if("alt-y-grid".equals(optName)) {
                boolean altYGrid = parseOptType(arg, optName, i, Boolean.FALSE, "").booleanValue();
                graphDef.setAltYGrid(altYGrid);
            }
            else if("units-length".equals(optName)) {
                int value = parseOptType(arg, optName, i, new Integer(0), "--units-length must be followed by a number").intValue();
                graphDef.setUnitsLength(value);
            }
            else if("units".equals(optName)) {
                String units = parseOptType(arg, optName, i, "", "--units must be followed by a unit for the SI (k, M)");
                graphDef.setUnit(units);
            }
            else if("x-grid".equals(optName)) {
                String xGrid = parseOptType(arg, optName, i, "", "--x-grid must be followed by an argument");
                parseXGrid(graphDef, xGrid);
            }
            else if("y-grid".equals(optName)) {
                String yGrid = parseOptType(arg, optName, i, "", "--y-grid must be followed by an argument");
                parseYGrid(graphDef, yGrid);
            }
            //no-gridfit
            //

            // right-axis
            // right-axis-format
            // 
            // force-rules-legend
            // legend-direction
            // imginfo
            // grid-dash
            // border
            // dynamic-labels
            // zoom
            // font-render-mode
            // font-smoothing-threshold
            // pango-markup
            else if(arg.startsWith("DEF:")) {
                GraphDefInformations infos = parseGraphDefElement(arg, 3, true);
                // LOG.debug("ds = {}", Arrays.toString(ds));
                final String replaced = infos.args[0].replaceAll("\\\\(.)", "$1");
                // LOG.debug("replaced = {}", replaced);

                final File dsFile;
                File rawPathFile = new File(replaced);
                if (rawPathFile.isAbsolute()) {
                    dsFile = rawPathFile;
                } else {
                    dsFile = new File(workDir, replaced);
                }
                // LOG.debug("dsFile = {}, ds[1] = {}", dsFile, ds[1]);

                final String absolutePath = (File.separatorChar == '\\')? dsFile.getAbsolutePath().replace("\\", "\\\\") : dsFile.getAbsolutePath();
                // LOG.debug("absolutePath = {}", absolutePath);
                if(infos.opts.size() > 0) {
                    RrdDb db;
                    try {
                        db = new RrdDb(dsFile.getCanonicalPath());
                    } catch (IOException e1) {
                        throw new RuntimeException();
                    }
                    String startString = infos.opts.get("start");
                    long frStart = start;
                    if(startString == null) {
                        frStart = new TimeParser(startString).parse().getTimestamp();
                    }
                    String endString = infos.opts.get("end");
                    long frEnd = end;
                    if(endString == null) {
                        frEnd = new TimeParser(endString).parse().getTimestamp();
                    }
                    //Needs to be improved, no way to use it in rrd4j
                    //String cfString = infos.opts.get("reduce");
                    //if(cfString == null) {
                    //    cfString = infos.args[2];
                    //}
                    //ConsolFun cf = ConsolFun.valueOf(cfString);
                    String stepString = infos.opts.get("step");
                    long frStep = step;
                    if(stepString == null) {
                        frStep = this.stringToType(stepString, new Long(step));
                    }

                    FetchData fd;
                    try {
                        fd = db.createFetchRequest(ConsolFun.valueOf(infos.args[2]), frStart, frEnd, frStep).fetchData();
                        graphDef.datasource(infos.name, infos.args[1], fd);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        db.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    graphDef.datasource(infos.name, absolutePath, infos.args[1], ConsolFun.valueOf(infos.args[2]));                    
                }
                List<String> defBits = new ArrayList<String>();
                defBits.add(absolutePath);
                defBits.add(infos.args[1]);
                defBits.add(infos.args[2]);
                defs.put(infos.name, defBits);
            } else if (arg.startsWith("VDEF:")) {
                GraphDefInformations infos = parseGraphDefElement(arg, 1, true);
                String[] expressionTokens = tokenize(infos.args[0], ",", false);
                addVdefDs(graphDef, infos.name, expressionTokens);
            } else if (arg.startsWith("CDEF:")) {
                GraphDefInformations infos = parseGraphDefElement(arg, 1, true);
                graphDef.datasource(infos.name, infos.args[0]);
                List<String> cdefBits = new ArrayList<String>();
                cdefBits.add(infos.args[0]);
                defs.put(infos.name, cdefBits);
            } else if (arg.startsWith("GPRINT:") || arg.startsWith("PRINT:") ) {
                doPrint(graphDef, arg);
            } else if (arg.startsWith("COMMENT:")) {
                String comments[] = tokenize(arg, ":", false);
                String format = comments[1].replaceAll("\\n", "\\\\l");
                graphDef.comment(format);
            } else if (LINEPATTERN.matcher(arg).find()) {
                doLine(graphDef, arg);
            } else if (arg.startsWith("AREA:")) {
                GraphDefInformations infos = parseGraphDefElement(arg, 2, true);
                boolean stack = false;
                if(infos.opts.containsKey("STACK"))
                    stack = true;
                String[] color = tokenize(infos.args[0], "#", true);
                graphDef.area(color[0], getColorOrInvisible(color, 1), infos.args[1] != null ? infos.args[1] : "", stack);
            } else if (arg.startsWith("STACK:")) {
                GraphDefInformations infos = parseGraphDefElement(arg, 2, true);
                String[] color = tokenize(infos.args[0], "#", true);
                graphDef.stack(color[0], getColorOrInvisible(color, 1), infos.args[1] != null ? infos.args[1] : "");
            } else if (arg.startsWith("HRULE:")) {
                String definition = arg.substring("HRULE:".length());
                String hrule[] = tokenize(definition, ":", true);
                String[] color = tokenize(hrule[0], "#", true);
                Double value = Double.valueOf(color[0]);
                graphDef.hrule(value, getColor(color[1]), hrule[1]);
            } else if (arg.endsWith("/rrdtool") || arg.equals("graph") || arg.equals("-")) {
                // ignore, this is just a leftover from the rrdtool-specific options

            } else {
                LOG.warn("RRD4J: Unrecognized graph argument: {}", arg);
            }

        }

        graphDef.setTimeSpan(start, end);
        graphDef.setMinValue(lowerLimit);
        graphDef.setMaxValue(upperLimit);
        graphDef.setRigid(rigid);
        graphDef.setHeight(height);
        graphDef.setWidth(width);

        LOG.debug("RRD4J Finished tokenizing checking: start time: {}, end time: {}", start, end);
        //LOG.debug("large font = {}, small font = {}", graphDef.getLargeFont(), graphDef.getSmallFont());
        return graphDef;
    }

    private void processRrdFontArgument(RrdGraphDef graphDef, String argParm) {
        String[] argValue = tokenize(argParm, ":", true);
        float newPointSize = stringToType(argValue[1], 0f);
        FontTag ft = FontTag.valueOf(argValue[0]);
        if(argValue.length == 2 || argValue[2].trim().isEmpty()) {
            graphDef.setFont(ft, graphDef.getFont(ft).deriveFont(newPointSize));            
        } else {
            try {
                Font font;
                if(FONTS.contains(argValue[2])) {
                    //derive take float, constructor an int
                    font = new Font(argValue[2], Font.PLAIN, 1).deriveFont(newPointSize);
                }
                else {
                    font = Font.createFont(Font.TRUETYPE_FONT, new File(argValue[2]));
                }
                graphDef.setFont(ft, font);
            } catch (Throwable e) {
                // oh well, fall back to existing font stuff
                LOG.warn("unable to create font from font argument {}", argParm, e);
            }
        }
    }

    private String[] tokenize(final String line, final String delimiters, final boolean processQuotes) {
        String passthroughTokens = "lLcrjgsJ"; /* see org.rrd4J.graph.RrdGraphConstants.MARKERS */
        return tokenizeWithQuotingAndEscapes(line, delimiters, processQuotes, passthroughTokens);
    }

    /**
     * @param colorArg Should have the form COLORTAG#RRGGBB[AA]
     * @see <a href="http://oss.oetiker.ch/rrdtool/doc/rrdgraph.en.html">rrdgraph man page</a> 
     */
    private void parseGraphColor(final RrdGraphDef graphDef, final String colorArg) throws IllegalArgumentException {
        // Parse for format COLORTAG#RRGGBB[AA]
        String[] colorArgParts = tokenize(colorArg, "#", false);
        if (colorArgParts.length != 2) {
            throw new IllegalArgumentException("--color must be followed by value with format COLORTAG#RRGGBB[AA]");
        }

        String colorTag = colorArgParts[0].toUpperCase();
        String colorHex = colorArgParts[1].toUpperCase();

        // validate hex color input is actually an RGB hex color value
        if (colorHex.length() != 6 && colorHex.length() != 8) {
            throw new IllegalArgumentException("--color must be followed by value with format COLORTAG#RRGGBB[AA]");
        }

        // this might throw NumberFormatException, but whoever wrote
        // createGraph didn't seem to care, so I guess I don't care either.
        // It'll get wrapped in an RrdException anyway.
        Color color = getColor(colorHex);

        // These are the documented RRD color tags
        try {

            if (COLORNAMES.contains(colorTag.toLowerCase())) {
                graphDef.setColor(colorTag, color);
            }
            else {
                throw new RuntimeException("Unknown color tag " + colorTag);
            }
        } catch (Throwable e) {
            LOG.error("RRD4J: exception occurred creating graph", e);
        }
    }

    /**
     * This implementation does not track any stats.
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getStats() {
        return "";
    }

    /*
     * These offsets work for ranger@ with Safari and JRobin 1.5.8.
     */
    /**
     * <p>getGraphLeftOffset</p>
     *
     * @return a int.
     */
    @Override
    public int getGraphLeftOffset() {
        return 74;
    }

    /**
     * <p>getGraphRightOffset</p>
     *
     * @return a int.
     */
    @Override
    public int getGraphRightOffset() {
        return -15;
    }

    /**
     * <p>getGraphTopOffsetWithText</p>
     *
     * @return a int.
     */
    @Override
    public int getGraphTopOffsetWithText() {
        return -61;
    }

    /**
     * <p>getDefaultFileExtension</p>
     *
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getDefaultFileExtension() {
        return ".rrd";
    }

    /**
     * <p>tokenizeWithQuotingAndEscapes</p>
     *
     * @param line a {@link java.lang.String} object.
     * @param delims a {@link java.lang.String} object.
     * @param processQuoted a boolean.
     * @return an array of {@link java.lang.String} objects.
     */
    public static String[] tokenizeWithQuotingAndEscapes(String line, String delims, boolean processQuoted) {
        return tokenizeWithQuotingAndEscapes(line, delims, processQuoted, "");
    }

    /**
     * Tokenize a {@link String} into an array of {@link String}s.
     *
     * @param line
     *          the string to tokenize
     * @param delims
     *          a string containing zero or more characters to treat as a delimiter
     * @param processQuoted
     *          whether or not to process escaped values inside quotes
     * @param tokens
     *          custom escaped tokens to pass through, escaped.  For example, if tokens contains "lsg", then \l, \s, and \g
     *          will be passed through unescaped.
     * @return an array of {@link java.lang.String} objects.
     */
    public static String[] tokenizeWithQuotingAndEscapes(final String line, final String delims, final boolean processQuoted, final String tokens) {
        List<String> tokenList = new LinkedList<String>();

        StringBuffer currToken = new StringBuffer();
        boolean quoting = false;
        boolean escaping = false;
        boolean debugTokens = Boolean.getBoolean("org.opennms.netmgt.rrd.debugTokens");
        if (!LOG.isDebugEnabled())
            debugTokens = false;

        if (debugTokens)
            LOG.debug("tokenize: line={}, delims={}", line, delims);
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (debugTokens)
                LOG.debug("tokenize: checking char: {}", ch);
            if (escaping) {
                if (ch == 'n') {
                    currToken.append(escapeIfNotPathSepInDEF(ch, '\n', currToken));
                } else if (ch == 'r') {
                    currToken.append(escapeIfNotPathSepInDEF(ch, '\r', currToken));
                } else if (ch == 't') {
                    currToken.append(escapeIfNotPathSepInDEF(ch, '\t', currToken));
                } else {
                    if (tokens.indexOf(ch) >= 0) {
                        currToken.append('\\').append(ch);
                    } else if (currToken.toString().startsWith("DEF:")) {
                        currToken.append('\\').append(ch);
                    } else {
                        // silently pass through the character *without* the \ in front of it
                        currToken.append(ch);
                    }
                }
                escaping = false;
                if (debugTokens)
                    LOG.debug("tokenize: escaped. appended to {}", currToken);
            } else if (ch == '\\') {
                if (debugTokens)
                    LOG.debug("tokenize: found a backslash... escaping currToken = {}", currToken);
                if (quoting && !processQuoted)
                    currToken.append(ch);
                else
                    escaping = true;
            } else if (ch == '\"') {
                if (!processQuoted)
                    currToken.append(ch);
                if (quoting) {
                    if (debugTokens)
                        LOG.debug("tokenize: found a quote ending quotation currToken = {}", currToken);
                    quoting = false;
                } else {
                    if (debugTokens)
                        LOG.debug("tokenize: found a quote beginning quotation  currToken = {}", currToken);
                    quoting = true;
                }
            } else if (!quoting && delims.indexOf(ch) >= 0) {
                if (debugTokens)
                    LOG.debug("tokenize: found a token: {} ending token [{}] and starting a new one", ch, currToken);
                tokenList.add(currToken.toString());
                currToken = new StringBuffer();
            } else {
                if (debugTokens)
                    LOG.debug("tokenize: appending {} to token: {}", ch, currToken);
                currToken.append(ch);
            }

        }

        if (escaping || quoting) {
            if (debugTokens)
                LOG.debug("tokenize: ended string but escaping = {} and quoting = {}", escaping, quoting);
            throw new IllegalArgumentException("unable to tokenize string " + line + " with token chars " + delims);
        }

        if (debugTokens)
            LOG.debug("tokenize: reached end of string.  completing token {}", currToken);
        tokenList.add(currToken.toString());

        return (String[]) tokenList.toArray(new String[tokenList.size()]);
    }

    /**
     * <p>escapeIfNotPathSepInDEF</p>
     *
     * @param encountered a char.
     * @param escaped a char.
     * @param currToken a {@link java.lang.StringBuffer} object.
     * @return an array of char.
     */
    public static char[] escapeIfNotPathSepInDEF(final char encountered, final char escaped, final StringBuffer currToken) {
        if ( ('\\' != File.separatorChar) || (! currToken.toString().startsWith("DEF:")) ) {
            return new char[] { escaped };
        } else {
            return new char[] { '\\', encountered };
        }
    }

    /**
     * Add a VDEF.
     * @param graphDef the current graphdef
     * @param sourceName the name of the VDEF 
     * @param rhs the RPN expression splitted in tokens
     */
    protected void addVdefDs(RrdGraphDef graphDef, String sourceName, String[] rhs) {
        if (rhs.length == 2) {
            try {
                Variable v = VDEFOPERATORS.get(rhs[1]).newInstance();
                graphDef.datasource(sourceName, rhs[0], v);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid VDEF operator: " + rhs[1]);
            }
        } else if (rhs.length == 3 && "PERCENT".equals(rhs[2])) {
            double pctRank = Double.valueOf(rhs[1]);
            Variable var = new Variable.PERCENTILE(pctRank);
            graphDef.datasource(sourceName, rhs[0], var);
        } else if (rhs.length == 3 && "PERCENTNAN".equals(rhs[2])) {
            double pctRank = Double.valueOf(rhs[1]);
            Variable var = new Variable.PERCENTILENAN(pctRank);
            graphDef.datasource(sourceName, rhs[0], var);
        }
    }

    private void parseYGrid(RrdGraphDef graphDef, String gridString) {
        if ( gridString.equalsIgnoreCase("none") ) {
            graphDef.setDrawYGrid(false);
            return;
        }
        String[] tokens = tokenize(gridString, ":", true);
        if (tokens.length != 2) {
            throw new IllegalArgumentException("Invalid YGRID settings: " + gridString);
        }
        double gridStep = stringToType(tokens[0], Double.NaN);
        int labelFactor = stringToType(tokens[1], (Integer) null);
        graphDef.setValueAxis(gridStep, labelFactor);
    }

    private void parseXGrid(RrdGraphDef graphDef, String gridString) {
        if ( gridString.equalsIgnoreCase("none") ) {
            graphDef.setDrawXGrid(false);
            return;
        }
        String[] tokens = tokenize(gridString, ":", true);
        if (tokens.length != 8) {
            throw new IllegalArgumentException("Invalid XGRID settings: " + gridString);
        }
        int minorUnit = resolveUnit(tokens[0]);
        int majorUnit = resolveUnit(tokens[2]);
        int labelUnit = resolveUnit(tokens[4]);
        int minorUnitCount = stringToType(tokens[1], Integer.MIN_VALUE);
        int majorUnitCount = stringToType(tokens[3], Integer.MIN_VALUE);
        int labelUnitCount = stringToType(tokens[5], Integer.MIN_VALUE);
        int labelSpan = stringToType(tokens[6], Integer.MIN_VALUE);
        String fmt = tokens[7];
        graphDef.setTimeAxis(minorUnit, minorUnitCount, majorUnit, majorUnitCount,
                labelUnit, labelUnitCount, labelSpan, fmt);
    }
    
    @SuppressWarnings("deprecation")
    private void doPrint(RrdGraphDef graphDef, String printString) {
        GraphDefInformations infos = parseGraphDefElement(printString, 3, false);
        String srcName = infos.args[0];
        String format;
        ConsolFun cf = null;
        if(infos.args[2] != null) {
            cf = ConsolFun.valueOf(infos.args[1]);
            format = infos.args[2];
        }
        else {
            format = infos.args[1];
        }
        format = format.replaceAll("%(\\d*\\.\\d*)lf", "@$1");
        format = format.replaceAll("%s", "@s");
        format = format.replaceAll("%%", "%");
        //LOG.debug("gprint: oldformat = {} newformat = {}", gprint[2], format);
        format = format.replaceAll("\\n", "\\\\l");
        if(cf != null) {
            if("GPRINT".equals(infos.type)) {
                graphDef.gprint(srcName, cf, format);                                            
            }
            else {
                graphDef.print(srcName, cf, format);                    
            }
        } else {
            if("GPRINT".equals(infos.type)) {
                graphDef.gprint(srcName, format);                    
            }
            else {
                graphDef.print(srcName, format);                    
            }
        }
        
    }
    
    private void doLine(RrdGraphDef graphDef, String lineString) {
        GraphDefInformations infos = parseGraphDefElement(lineString, 2, true);
        float lineWidth = stringToType(infos.type.substring(4), Float.NaN).floatValue();
        boolean stack = false;
        if(infos.opts.containsKey("STACK"))
            stack = true;
        BasicStroke stroke;
        //Options contains dashes, try to resolve it
        if(infos.opts.containsKey("dashes")) {
            String dashesCmd = infos.opts.get("dashes");
            String dashOffsetCmd = infos.opts.get("dashe-offset");
            float[] dashes;
            if( ! dashesCmd.trim().isEmpty()) {
                String[] dashesElements = tokenize(dashesCmd, ",", false);
                dashes = new float[dashesElements.length];
                for(int i= 0; i < dashesElements.length; i++) {
                    dashes[i] = stringToType(dashesElements[i], (Float) null);
                }                
            } else {
                // the rrdtool default dash
                dashes = new float[] { 5.0f };
            }
            float dashOffset = stringToType(dashOffsetCmd, 0f);
            stroke = new BasicStroke(lineWidth, DEFAULTSTROKE.getEndCap(), DEFAULTSTROKE.getLineJoin(), DEFAULTSTROKE.getMiterLimit(), dashes, dashOffset);
        } else {
            stroke = new BasicStroke(lineWidth);
        }
        String[] color = tokenize(infos.args[0], "#", true);
        graphDef.line(color[0], getColorOrInvisible(color, 1), infos.args[1] != null ? infos.args[1] : "", stroke, stack);
    }

    private int resolveUnit(String unitName) {
        final String[] unitNames = {"SECOND", "MINUTE", "HOUR", "DAY", "WEEK", "MONTH", "YEAR"};
        final int[] units = {Calendar.SECOND, Calendar.MINUTE, Calendar.HOUR, Calendar.DAY_OF_MONTH, Calendar.WEEK_OF_YEAR, Calendar.MONTH, Calendar.YEAR};
        for (int i = 0; i < unitNames.length; i++) {
            if (unitName.equalsIgnoreCase(unitNames[i])) {
                return units[i];
            }
        }
        throw new IllegalArgumentException("Unknown time unit specified: " + unitName);
    }

}
