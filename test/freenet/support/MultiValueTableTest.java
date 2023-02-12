/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package freenet.support;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * Test case for {@link freenet.support.MultiValueTable} class.
 *
 * @author Alberto Bacchelli &lt;sback@freenetproject.org&gt;
 */
public class MultiValueTableTest {

    private static final int sampleKeyNumber = 100;
    private static final int sampleMaxValueNumber = 3;
    private static final boolean sampleIsRandom = true;
    private static final int NON_EXISTING_KEY = -42;
    private final Random rnd = new Random(12345);

    private Map<Integer, List<Object>> sampleObjects;
    private MultiValueTable<Integer, Object> multiValueTable;


    @Before
    public void setUp() throws Exception {
        sampleObjects = createSampleKeyMultiVal(sampleKeyNumber, sampleMaxValueNumber, sampleIsRandom);
        multiValueTable = fillMultiValueTable(sampleObjects);
    }

    /**
     * Create a Map filled with increasing Integers as keys
     * and a List of generic Objects as values.
     *
     * @param keysCount   the number of keys to create
     * @param valuesCount the maximum value number per key
     * @param isRandom    if true each key could have [1,valuesCount] values
     *                    chosen randomly, if false each key will have valuesCount values
     * @return the Map created
     */
    private Map<Integer, List<Object>> createSampleKeyMultiVal(int keysCount, int valuesCount, boolean isRandom) {
        Map<Integer, List<Object>> sampleObjects = new LinkedHashMap<>(keysCount);
        int methodValuesNumber = valuesCount;
        for (int i = 0; i < keysCount; i++) {
            if (isRandom) {
                methodValuesNumber = 1 + rnd.nextInt(valuesCount);
            }
            sampleObjects.put(i, fillSampleValuesList(methodValuesNumber));
        }
        return sampleObjects;
    }

    /**
     * Create a sample List filled
     * with the specified number of
     * generic objects
     *
     * @param valuesCount number of objects to create
     * @return the sample List
     */
    private static List<Object> fillSampleValuesList(int valuesCount) {
        List<Object> sampleValues = new ArrayList<>(valuesCount);
        for (int i = 0; i < valuesCount; i++) {
            sampleValues.add(new Object());
        }
        return sampleValues;
    }

    /**
     * Fill a new MultiValueTable from a Object[][] provided.
     * The Object[][] must be in the same form generated by
     * createSampleKeyMultiVal method.
     *
     * @param sampleObjects Object[][] array, with [i][0] as key and [i][1] as list of values
     * @return the created MultiValueTable
     */
    private MultiValueTable<Integer, Object> fillMultiValueTable(Map<Integer, List<Object>> sampleObjects) {
        MultiValueTable<Integer, Object> multiValueTable = new MultiValueTable<>(sampleObjects.size());
        for (Map.Entry<Integer, List<Object>> entries : sampleObjects.entrySet()) {
            multiValueTable.putAll(entries.getKey(), entries.getValue());
        }
        return multiValueTable;
    }

    @Test
    public void testPut() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        multiValueTable.put(1, "two");
        multiValueTable.put(3, "three");
        multiValueTable.put(4, "four");
        assertEquals(3, multiValueTable.size());
        assertEquals(2, multiValueTable.countAll(1));
        assertEquals(Arrays.asList("one", "two"), multiValueTable.getAll(1));
        assertEquals(Collections.singletonList("three"), multiValueTable.getAll(3));
        assertEquals(Collections.singletonList("four"), multiValueTable.getAll(4));
    }

    @Test
    public void getFirstReturnsNullForNonExistingKey() {
        MultiValueTable<Integer, Object> table = new MultiValueTable<>();
        assertNull(table.getFirst(NON_EXISTING_KEY));
    }

    @Test
    public void testGetFirst() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            assertEquals(
                entry.getValue().get(0),
                multiValueTable.getFirst(entry.getKey())
            );
        }
    }

    @Test
    public void containsKeyReturnsFalseForNonExistingKey() {
        assertFalse(multiValueTable.containsKey(NON_EXISTING_KEY));
    }

    @Test
    public void testContainsKey() {
        for (Integer key : sampleObjects.keySet()) {
            assertTrue(multiValueTable.containsKey(key));
        }
    }

    @Test
    public void containsElementReturnsFalseForNonExistingKey() {
        assertFalse(multiValueTable.containsElement(NON_EXISTING_KEY, new Object()));
    }


    @Test
    public void testContainsElement() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            for (Object element : entry.getValue()) {
                assertTrue(
                    multiValueTable.containsElement(entry.getKey(), element)
                );
            }
        }
    }

    @Test
    public void getAllMethodReturnsEmptyListForNonExistentKey() {
        List<Object> elements = multiValueTable.getAll(NON_EXISTING_KEY);
        assertNotNull(elements);
        assertTrue(elements.isEmpty());
    }

    @Test
    public void testGetAll() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            assertEquals(entry.getValue(), multiValueTable.getAll(entry.getKey()));
        }
    }

    @Test
    public void countAllMethodReturnsZeroForNonExistentKey() {
        assertEquals(0, multiValueTable.countAll(NON_EXISTING_KEY));
    }

    @Test
    public void testCountAll() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            assertEquals(entry.getValue().size(), multiValueTable.countAll(entry.getKey()));
        }
    }

    @Test
    public void removeWorksForNonExistingKey() {
        assertFalse(multiValueTable.remove(NON_EXISTING_KEY));
    }

    @Test
    public void testRemove() {
        for (Integer key : sampleObjects.keySet()) {
            assertTrue(multiValueTable.remove(key));
        }
        assertTrue(multiValueTable.isEmpty());
    }

    @Test
    public void isEmptyReturnsTrueForEmptyTable() {
        multiValueTable = new MultiValueTable<>();
        assertTrue(multiValueTable.isEmpty());
    }

    @Test
    public void testIsEmpty() {
        assertFalse(multiValueTable.isEmpty());
        multiValueTable.clear();
        assertTrue(multiValueTable.isEmpty());
    }


    @Test
    public void testClear() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        multiValueTable.clear();
        assertNull(multiValueTable.getFirst(1));
    }

    @Test
    public void removeElementCanBeExecutedForNonExistingKey() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        assertFalse(
            multiValueTable.removeElement(NON_EXISTING_KEY, "one")
        );
        assertEquals("one", multiValueTable.getFirst(1));
    }

    @Test
    public void removeElementCanBeExecutedForNonExistingElement() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        assertFalse(
            multiValueTable.removeElement(1, "two")
        );
        assertEquals("one", multiValueTable.getFirst(1));
    }

    @Test
    public void testRemoveElement() {
        for (Map.Entry<Integer, List<Object>> entry : sampleObjects.entrySet()) {
            for (Object element : entry.getValue()) {
                assertTrue(
                    multiValueTable.removeElement(entry.getKey(), element)
                );
            }
        }
        assertTrue(multiValueTable.isEmpty());
    }

    @Test
    public void keysMethodReturnsEmptyCollectionForEmptyTable() {
        multiValueTable = new MultiValueTable<>();
        Set<Integer> keys = multiValueTable.keys();
        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }

    @Test
    public void testKeys() {
        assertEquals(
            sampleObjects.keySet(),
            multiValueTable.keys()
        );
    }

    /**
     * Tests elements() and keys() method
     * verifying their behavior when putting the same
     * value for different keys.
     */
    @Test
    public void testDifferentKeysSameElement() {
        int keysCount = 2;
        MultiValueTable<Object, Object> methodMVTable = new MultiValueTable<>();
        String sampleValue = "sampleValue";
        //putting the same value for different keys
        for (int i = 0; i < keysCount; i++) {
            methodMVTable.put(new Object(), sampleValue);
        }

        assertEquals(keysCount, methodMVTable.keys().size());

        int elementCount = 0;
        for (Object key : methodMVTable.keys()) {
            elementCount += methodMVTable.countAll(key);
        }
        assertEquals(2, elementCount);
    }

    @Test
    public void invokeToString() {
        multiValueTable = new MultiValueTable<>();
        multiValueTable.put(1, "one");
        multiValueTable.put(2, "two");
        multiValueTable.putAll(3, Arrays.asList("three", "tres"));

        String str = multiValueTable.toString();
        assertNotNull(str);
        assertFalse(str.isEmpty());
        for (Object token : Arrays.asList(
            1, "one",
            2, "two",
            3, "three", "tres"
        )) {
            assertTrue(str.contains(token.toString()));
        }
    }
}
