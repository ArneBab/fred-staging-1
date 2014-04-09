package freenet.node;

import junit.framework.TestCase;

/**
 * User: robert
 * Date: 2014/04/09
 * Time: 12:20 PM
 */
public
class LocationTest extends TestCase
{
    public
    void testDistanceSanity()
    {
        double a=0.3833;
        double b=0.3832;

        double distance=Location.distance(a, b);
        assertTrue(distance > 0.000);
        assertTrue(distance < 0.001);

        distance=Location.distance(b, a);
        assertTrue(distance > 0.000);
        assertTrue(distance < 0.001);

        //

        a=1.0;
        b=0.0;

        distance=Location.distance(a, b);
        assertTrue(distance > -0.001);
        assertTrue(distance <  0.001);

        distance=Location.distance(b, a);
        assertTrue(distance > -0.001);
        assertTrue(distance <  0.001);

        //

        a=0.25;
        b=0.75;

        distance=Location.distance(a, b);
        assertTrue(distance > 0.499);
        assertTrue(distance < 0.501);
    }
}
