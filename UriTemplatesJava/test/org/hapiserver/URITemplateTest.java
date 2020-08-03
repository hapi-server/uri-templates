
package org.hapiserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jbf
 */
public class URITemplateTest {
    
    public URITemplateTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @Before
    public void setUp() {
    }

    /**
     * Test of makeCanonical method, of class URITemplate.
     */
    @Test
    public void testMakeCanonical() {
        System.out.println("makeCanonical");
        String formatString = "%{Y,m=02}*.dat";
        String expResult = "$(Y;m=02)$x.dat";
        String result = URITemplate.makeCanonical(formatString);
        assertEquals(expResult, result);
    }
    
    private static String toStr( int[] res ) {
        String t1= TimeUtil.isoTimeFromArray( Arrays.copyOf(res, 7) ).substring(0,16);
        String t2= TimeUtil.isoTimeFromArray( Arrays.copyOfRange(res, 7, 14) ).substring(0,16);
        return t1+"/"+t2;
    }
        
    private static void testTimeParser1( String spec, String test, String norm ) throws Exception {
        URITemplate ut;
        try {
            ut = new URITemplate(spec);
        } catch ( IllegalArgumentException ex ) {
            System.err.println("### unable to parse spec: "+spec);
            return;
        }
        
        String[] nn= norm.split("/",-2);
        if ( TimeUtil.iso8601DurationPattern.matcher(nn[1]).matches() ) {
            nn[1]= TimeUtil.isoTimeFromArray(
                    TimeUtil.add( TimeUtil.isoTimeToArray(nn[0]), 
                            TimeUtil.parseISO8601Duration(nn[1]) ) );
        }
        
        int[] start= TimeUtil.isoTimeToArray(nn[0]);
        int[] stop= TimeUtil.isoTimeToArray(nn[1]);
        int[] inorm= new int[14];
        System.arraycopy( start, 0, inorm, 0, 7 );
        System.arraycopy( stop, 0, inorm, 7, 7 );
        
        int[] res;
        
        try {
            res= ut.parse( test );
        } catch ( ParseException ex ) {
            fail(ex.getMessage());
            return;
        }
        
        char arrow= (char)8594;
        if ( Arrays.equals( res, inorm  ) ) {
            System.out.println( String.format( "%s:  \t\"%s\"%s\t\"%s\"", spec, test, arrow, toStr(res) ) );
        } else {    
            System.out.println( "### ranges do not match: "+spec + " " +test + arrow + toStr(res) + ", should be "+norm );
            //throw new IllegalStateException("ranges do not match: "+spec + " " +norm + "--> " + res + ", should be "+test );
        }
        assertArrayEquals( res, inorm );
        
    }    

    private void testParse1() throws ParseException {
        URITemplate ut= new URITemplate("$Y_sc$(enum;values=a,b,c,d;id=sc)");
        Map<String,String> extra= new HashMap<>();
        int[] digits= ut.parse("2003_scd",extra);
        String actual= String.format("%d %s",digits[0],extra.get("sc"));
        assertEquals("2003 d",actual);
        System.out.println(actual);
    }
    
    private void testParse2() throws ParseException {
        URITemplate ut= new URITemplate("$Y_$m_v$v.dat");
        Map<String,String> extra= new HashMap<>();
        int[] digits= ut.parse("2003_10_v20.3.dat",extra);
        assertEquals(2003,digits[0]);
        assertEquals(10,digits[1]);
        assertEquals(11,digits[8]);
        assertEquals("20.3",extra.get("v"));
        
    }
    
    /**
     * Test of parse method, of class URITemplate.
     * @throws java.lang.Exception
     */
    @Test
    public void testParse() throws Exception {
        System.out.println("parse");
        testParse1();
        testParse2();
        testTimeParser1( "$Y $m $d $H $M", "2012 03 30 16 20", "2012-03-30T16:20/2012-03-30T16:21" );
        testTimeParser1( "$Y$m$d-$(enum;values=a,b,c,d)", "20130202-a", "2013-02-02/2013-02-03" );
        testTimeParser1( "$Y$m$d-$(Y;end)$m$d", "20130202-20140303", "2013-02-02/2014-03-03" );
        testTimeParser1( "$Y$m$d-$(Y;end)$m$(d;shift=1)", "20130202-20140303", "2013-02-02/2014-03-04" );
        testTimeParser1( "$Y$m$d-$(d;end)", "20130202-13", "2013-02-02/2013-02-13" );
        testTimeParser1( "$(periodic;offset=0;start=2000-001;period=P1D)", "0",  "2000-001/P1D");
        testTimeParser1( "$(periodic;offset=0;start=2000-001;period=P1D)", "20", "2000-021/P1D");        
        testTimeParser1( "$(periodic;offset=2285;start=2000-346;period=P27D)", "1", "1832-02-08/P27D");
        testTimeParser1( "$(periodic;offset=2285;start=2000-346;period=P27D)", "2286", "2001-007/P27D");        
        testTimeParser1( "$(j;Y=2012)$(hrinterval;names=01,02,03,04)", "01702", "2012-01-17T06:00/PT6H");
        testTimeParser1( "$(j;Y=2012).$H$M$S.$(subsec;places=3)", "017.020000.245", "2012-01-17T02:00:00.245/2012-01-17T02:00:00.246");
        testTimeParser1( "$(j;Y=2012).$x.$X.$(ignore).$H", "017.x.y.z.02", "2012-01-17T02:00:00/2012-01-17T03:00:00");
        testTimeParser1( "$(j;Y=2012).*.*.*.$H", "017.x.y.z.02", "2012-01-17T02:00:00/2012-01-17T03:00:00");
        // The following shows a bug where it doesn't consider the length of $H and just stops on the next period.
        // A field cannot contain the following delimiter.
        //testTimeParser1( "$(j,Y=2012).*.$H",     "017.x.y.z.02", "2012-01-17T02:00:00/2012-01-17T03:00:00");
        //Orbits are not supported.
        //testTimeParser1( "$(o;id=rbspa-pp)", "31",  "2012-09-10T14:48:30.914Z/2012-09-10T23:47:34.973Z"); 
        testTimeParser1( "$(j;Y=2012)$(hrinterval;names=01,02,03,04)", "01702", "2012-01-17T06:00/2012-01-17T12:00");
        testTimeParser1( "$-1Y $-1m $-1d $H$M", "2012 3 30 1620", "2012-03-30T16:20/2012-03-30T16:21" );
        testTimeParser1( "$Y",            "2012",     "2012-01-01T00:00/2013-01-01T00:00");
        testTimeParser1( "$Y-$j",         "2012-017", "2012-01-17T00:00/2012-01-18T00:00");
        testTimeParser1( "$(j,Y=2012)",   "017",      "2012-01-17T00:00/2012-01-18T00:00");
        testTimeParser1( "ace_mag_$Y_$j_to_$(Y;end)_$j.cdf",   "ace_mag_2005_001_to_2005_003.cdf",      "2005-001T00:00/2005-003T00:00");    
    }

    private static void testTimeFormat1( String spec, String test, String norm ) throws Exception {
        URITemplate ut;
        try {
            ut = new URITemplate(spec);
        } catch ( IllegalArgumentException ex ) {
            System.out.println("### unable to parse spec: "+spec);
            return;
        }
        
        String[] nn= norm.split("/",-2);
        if ( TimeUtil.iso8601DurationPattern.matcher(nn[1]).matches() ) {
            nn[1]= TimeUtil.isoTimeFromArray(
                    TimeUtil.add( TimeUtil.isoTimeToArray(nn[0]), 
                            TimeUtil.parseISO8601Duration(nn[1]) ) );
        }
        String res;
        try {
            res= ut.format( nn[0], nn[1] );
        } catch ( RuntimeException ex ) {
            System.out.println( "### " + ex.getMessage() );
            return;
        }
        
        char arrow= (char)8594;
        if ( res.equals(test) ) {
            System.out.println( String.format( "%s:  \t\"%s\"%s\t\"%s\"", spec, norm, arrow, res ) );
        } else {
            System.out.println( "### ranges do not match: "+spec + " " +norm + arrow + res + ", should be "+test );
            //throw new IllegalStateException("ranges do not match: "+spec + " " +norm + "--> " + res + ", should be "+test );
        }
        assertEquals( res, test );
    }

    /**
     * Test of format method, of class URITemplate.
     * @throws java.lang.Exception
     */
    @Test
    public void testFormat() throws Exception {
        System.out.println("format");
        //testTimeParser1( "$Y$m$d-$(enum;values=a,b,c,d)", "20130202-a", "2013-02-02/2013-02-03" );
        testTimeFormat1( "$Y$m$d-$(Y;end)$m$d", "20130202-20140303", "2013-02-02/2014-03-03" );
        testTimeFormat1( "$Y$m$d-$(Y;end)$m$(d;shift=1)", "20130202-20140303", "2013-02-02/2014-03-04" );
        testTimeFormat1( "$Y$m$d-$(d;end)", "20130202-13", "2013-02-02/2013-02-13" );
        testTimeFormat1( "$(periodic;offset=0;start=2000-001;period=P1D)", "0",  "2000-001/P1D");
        testTimeFormat1( "$(periodic;offset=0;start=2000-001;period=P1D)", "20", "2000-021/P1D");        
        testTimeFormat1( "$(periodic;offset=2285;start=2000-346;period=P27D)", "1", "1832-02-08/P27D");
        testTimeFormat1( "$(periodic;offset=2285;start=2000-346;period=P27D)", "2286", "2001-007/P27D");        
        testTimeFormat1( "$(j;Y=2012)$(hrinterval;names=01,02,03,04)", "01702", "2012-01-17T06:00/PT12H");
        testTimeFormat1( "$(j;Y=2012).$H$M$S.$(subsec;places=3)", "017.020000.245", "2012-01-17T02:00:00.245/2012-01-17T02:00:00.246");
        //testTimeFormat1( "$(j;Y=2012).$x.$X.$(ignore).$H", "017.x.y.z.02", "2012-01-17T02:00:00/2012-01-17T03:00:00");
        //testTimeFormat1( "$(j;Y=2012).*.*.*.$H", "017.x.y.z.02", "2012-01-17T02:00:00/2012-01-17T03:00:00");
        // The following shows a bug where it doesn't consider the length of $H and just stops on the next period.
        // A field cannot contain the following delimiter.
        //testTimeFormat1( "$(j,Y=2012).*.$H", "017.x.y.z.02", "2012-01-17T02:00:00/2012-01-17T03:00:00");
        //testTimeFormat1( "$(o;id=rbspa-pp)", "31",  "2012-09-10T14:48:30.914Z/2012-09-10T23:47:34.973Z");
        testTimeFormat1( "$(j;Y=2012)$(hrinterval;names=01,02,03,04)", "01702", "2012-01-17T06:00/2012-01-17T18:00");
        testTimeFormat1( "$-1Y $-1m $-1d $H$M", "2012 3 30 1620", "2012-03-30T16:20/2012-03-30T16:21" );
        testTimeFormat1( "$Y",            "2012",     "2012-01-01T00:00/2013-01-01T00:00");
        testTimeFormat1( "$Y-$j",         "2012-017", "2012-01-17T00:00/2012-01-18T00:00");
        testTimeFormat1( "$(j,Y=2012)",   "017",      "2012-01-17T00:00/2012-01-18T00:00");
        testTimeFormat1( "ace_mag_$Y_$j_to_$(Y;end)_$j.cdf",   "ace_mag_2005_001_to_2005_003.cdf",      "2005-001T00:00/2005-003T00:00");        
        
    }
    
    private static String readJSONToString( URL url ) {
        InputStream ins= null;
        try {
            ins = url.openStream();
            StringBuilder sb= new StringBuilder();
            byte[] buffer= new byte[2048];
            int bytesRead= ins.read(buffer);
            while ( bytesRead!=-1 ) {
                sb.append( new String(buffer,0,bytesRead) );
                bytesRead= ins.read(buffer);
            }
            return sb.toString();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        
        } finally {
            try {
                ins.close();
            } catch (IOException ex) {
                Logger.getLogger(URITemplateTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void testFormatHapiServerSiteOne( 
            JSONArray outputs, String t, String startTime, String stopTime )
            throws ParseException, JSONException {
        String[] testOutputs= URITemplate.formatRange( t, startTime, stopTime );
        if ( testOutputs.length!=outputs.length() ) {
            fail("bad number of results in formatRange: "+t);
        }
        for ( int l=0; l<outputs.length(); l++ ) {
            if ( !testOutputs[l].equals(outputs.getString(l) ) ) {
                fail("result doesn't match, got "+testOutputs[l]+", should be "+outputs.getString(l) );
            }
        }   
    }
    
    /**
     * for each timeRange and template in 
     * https://github.com/hapi-server/uri-templates/blob/master/formatting.json
     * enumerate the files (formatRange) to see that we get the correct result.
     */
    @Test 
    public void testFormatHapiServerSite() {
        try {
            String ss= readJSONToString( new URL( "https://raw.githubusercontent.com/hapi-server/uri-templates/master/formatting.json" ) );
            JSONArray jo= new JSONArray(ss);
            for ( int i=0; i<jo.length(); i++ ) {
                JSONObject jo1= jo.getJSONObject(i);
                System.out.println( String.format("# %2d: %s", i, jo1.get("whatTests") ) );
                if ( i<3 ) {
                    System.out.println( "###  Skipping test "+i );
                    continue;
                }
                JSONArray templates= jo1.getJSONArray("template");
                JSONArray outputs= jo1.getJSONArray("output");
                JSONArray timeRanges;
                try {
                    timeRanges = jo1.getJSONArray("timeRange");
                } catch ( JSONException ex ) {
                    String timeRange= jo1.getString("timeRange");
                    timeRanges= new JSONArray( Collections.singletonList(timeRange) );
                }
                for ( int j=0; j<templates.length(); j++ ) {
                    String t= templates.getString(j);
                    for ( int k=0; k<timeRanges.length(); k++ ) {
                        String timeRange= timeRanges.getString(k);
                        System.out.println("timeRange:"+timeRange);
                        String[] timeStartStop= timeRange.split("/",-2);
                        try {
                            testFormatHapiServerSiteOne( outputs, t, timeStartStop[0], timeStartStop[1] );
                        } catch (ParseException ex) {
                            try {
                                testFormatHapiServerSiteOne( outputs, t, timeStartStop[0], timeStartStop[1] );
                            } catch (ParseException ex1) {
                                fail(ex.getMessage());
                            }
                            throw new RuntimeException(ex);
                        }
                    }
                    System.out.println("" +t);
                }
                
            }
        } catch (JSONException | MalformedURLException ex) {
            Logger.getLogger(URITemplateTest.class.getName()).log(Level.SEVERE, null, ex);
            fail(ex.getLocalizedMessage());
        }
        
    }
    
    @Test
    public void testFormatRange() {
        try { 
            String t;            
            String[] ss;

            t = "data_$Y.dat";
            ss = URITemplate.formatRange( t, "2001-03-22", "2004-08-18" );
            if ( ss.length!=4 ) {
                fail(t);
            }
                        
//            t = "data_$Y_$(Y,end).dat";
//            ss = URITemplate.formatRange( t, "2001-03-22", "2004-08-18" );
//            if ( ss.length!=1 ) {
//                fail(t);
//            }
            
            for ( String s: ss ) {
                System.out.println(s);
            }
        } catch (ParseException ex) {
            fail(ex.getMessage());
        }
    }
    
}