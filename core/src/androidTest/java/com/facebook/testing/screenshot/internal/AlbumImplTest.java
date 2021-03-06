/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.testing.screenshot.internal;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.graphics.Bitmap;
import androidx.test.InstrumentationRegistry;
import java.io.File;
import java.util.Arrays;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Tests for {@link AlbumImpl} */
public class AlbumImplTest {
  private static final int BITMAP_DIMENSION = 10; /* pixels */
  private AlbumImpl mAlbumImpl;
  private Bitmap mSomeBitmap;
  private String mFooFile;
  private String mBarFile;
  private ScreenshotDirectories mScreenshotDirectories;

  @Before
  public void setUp() throws Exception {
    mScreenshotDirectories = new ScreenshotDirectories(InstrumentationRegistry.getTargetContext());
    mAlbumImpl = AlbumImpl.create(InstrumentationRegistry.getTargetContext(), "screenshots");
    mSomeBitmap = Bitmap.createBitmap(BITMAP_DIMENSION, BITMAP_DIMENSION, Bitmap.Config.ARGB_8888);
    mSomeBitmap.setPixel(1, 1, 0xff0000ff);

    mFooFile = mAlbumImpl.writeBitmap("foo", 0, 0, mSomeBitmap);
    mBarFile = mAlbumImpl.writeBitmap("bar", 0, 0, mSomeBitmap);
  }

  @After
  public void tearDown() throws Exception {
    mAlbumImpl.cleanup();
  }

  @Test
  public void testWriteTempBitmap() throws Throwable {
    Bitmap output = mAlbumImpl.getScreenshot(mAlbumImpl.writeBitmap("sfdf", 0, 0, mSomeBitmap));

    int actualBlueness = output.getPixel(1, 1) & 0xff;
    assertTrue("The pixel should be same accounting for compression", actualBlueness > 0xf0);
  }

  @Test
  public void testCleanupAndGet() throws Throwable {
    mAlbumImpl.addRecord(
        new RecordBuilderImpl(null).setName("foo").setTiling(Tiling.singleTile(mFooFile)));

    assertNotNull(mAlbumImpl.getScreenshot("foo"));
    mAlbumImpl.cleanup();
    assertEquals(null, mAlbumImpl.getScreenshot("foo"));
  }

  @Test
  public void testMultipleCleanups() throws Throwable {
    mAlbumImpl.addRecord(
        new RecordBuilderImpl(null).setName("foo").setTiling(Tiling.singleTile(mFooFile)));
    mAlbumImpl.cleanup();
    mAlbumImpl.cleanup();
  }

  @Test
  public void testNonExistentScreenshotReturnsNull() throws Throwable {
    assertEquals(null, mAlbumImpl.getScreenshot("mehmeh"));
    assertEquals(null, mAlbumImpl.getScreenshotFile("mehmeh"));
  }

  @Test
  public void testMetadataSaving() throws Throwable {
    mAlbumImpl.addRecord(
        new RecordBuilderImpl(null).setTiling(Tiling.singleTile(mFooFile)).setName("foo"));
    mAlbumImpl.addRecord(
        new RecordBuilderImpl(null).setTiling(Tiling.singleTile(mBarFile)).setName("bar"));

    mAlbumImpl.flush();
    Document document = parseMetadata();

    assertEquals(
        "bar",
        ((Element)
                ((Element)
                        ((Element) document.getElementsByTagName("screenshots").item(0))
                            .getElementsByTagName("screenshot")
                            .item(1))
                    .getElementsByTagName("name")
                    .item(0))
            .getTextContent());
  }

  @Test
  public void testSavesViewHierachy() throws Throwable {
    mAlbumImpl.writeViewHierarchyFile("foo", "");
    mAlbumImpl.addRecord(
        new RecordBuilderImpl(null).setName("foo").setTiling(Tiling.singleTile(mFooFile)));

    mAlbumImpl.flush();
    Document document = parseMetadata();

    String actual =
        ((Element)
                ((Element)
                        ((Element) document.getElementsByTagName("screenshots").item(0))
                            .getElementsByTagName("screenshot")
                            .item(0))
                    .getElementsByTagName("view_hierarchy")
                    .item(0))
            .getTextContent();

    assertEquals("foo_dump.json", actual);
  }

  @Test
  public void testSavesExtra() throws Throwable {
    RecordBuilderImpl rb = new RecordBuilderImpl(null);
    rb.setName("foo").setTiling(Tiling.singleTile(mFooFile)).addExtra("foo", "blah");

    mAlbumImpl.addRecord(rb);

    mAlbumImpl.flush();
    Document document = parseMetadata();

    assertEquals(
        "blah",
        getNestedElement(document.getDocumentElement(), "screenshot", "extras", "foo")
            .getTextContent());
  }

  @Test
  public void testSavesMultipleExtras() throws Throwable {
    RecordBuilderImpl rb = new RecordBuilderImpl(null);
    rb.setName("foo")
        .setTiling(Tiling.singleTile(mFooFile))
        .addExtra("foo", "blah")
        .addExtra("bar", "blah2");

    mAlbumImpl.addRecord(rb);

    mAlbumImpl.flush();
    Document document = parseMetadata();

    assertEquals(
        "blah",
        getNestedElement(document.getDocumentElement(), "screenshot", "extras", "foo")
            .getTextContent());

    assertEquals(
        "blah2",
        getNestedElement(document.getDocumentElement(), "screenshot", "extras", "bar")
            .getTextContent());
  }

  private Element getNestedElement(Element root, String... names) {
    if (names.length == 0) {
      return root;
    }

    Element next = (Element) root.getElementsByTagName(names[0]).item(0);
    assertNotNull("could not find " + names[0], next);
    return getNestedElement(next, Arrays.copyOfRange(names, 1, names.length));
  }

  @Test
  public void testErrorSaving() throws Throwable {
    mAlbumImpl.addRecord(new RecordBuilderImpl(null).setError("foobar"));
    mAlbumImpl.flush();
    Document document = parseMetadata();
    assertEquals(
        "foobar",
        ((Element)
                ((Element)
                        ((Element) document.getElementsByTagName("screenshots").item(0))
                            .getElementsByTagName("screenshot")
                            .item(0))
                    .getElementsByTagName("error")
                    .item(0))
            .getTextContent());
  }

  @Test
  public void testSavesGroup() throws Throwable {
    mAlbumImpl.addRecord(
        new RecordBuilderImpl(null)
            .setName("xyz")
            .setTiling(Tiling.singleTile(mFooFile))
            .setGroup("foo_bar"));

    mAlbumImpl.flush();

    Document document = parseMetadata();
    assertEquals(
        "foo_bar",
        ((Element)
                ((Element)
                        ((Element) document.getElementsByTagName("screenshots").item(0))
                            .getElementsByTagName("screenshot")
                            .item(0))
                    .getElementsByTagName("group")
                    .item(0))
            .getTextContent());
  }

  private Document parseMetadata() throws Throwable {
    File file = mScreenshotDirectories.get("screenshots");

    return DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new File(file, "metadata.xml"));
  }

  @Test
  public void testMultipleRecordsFromSameTestWithName() throws Throwable {
    mAlbumImpl.addRecord(
        new RecordBuilderImpl(null).setName("foo").setTiling(Tiling.singleTile(mFooFile)));

    try {
      mAlbumImpl.addRecord(
          new RecordBuilderImpl(null).setName("foo").setTiling(Tiling.singleTile(mFooFile)));
    } catch (AssertionError e) {
      OldApiBandaid.assertMatchesRegex(".*same name.*", e.getMessage());
      return;
    }
    fail("expected to see an exception");
  }

  @Test
  public void testRecordWithTiles() throws Throwable {
    final int WIDTH = 3;
    final int HEIGHT = 4;

    RecordBuilderImpl builder =
        new RecordBuilderImpl(null).setName("baz").setTiling(new Tiling(WIDTH, HEIGHT));

    for (int i = 0; i < WIDTH; i++) {
      for (int j = 0; j < HEIGHT; j++) {
        String tempName = mAlbumImpl.writeBitmap("baz", i, j, mSomeBitmap);
        builder.getTiling().setAt(i, j, tempName);
      }
    }

    mAlbumImpl.addRecord(builder);
    mAlbumImpl.flush();

    Document doc = parseMetadata();

    Element screenshots = (Element) doc.getElementsByTagName("screenshots").item(0);
    Element screenshot = (Element) screenshots.getElementsByTagName("screenshot").item(0);

    Element tileWidth = (Element) screenshot.getElementsByTagName("tile_width").item(0);
    Element tileHeight = (Element) screenshot.getElementsByTagName("tile_height").item(0);

    assertEquals(WIDTH, Integer.parseInt(tileWidth.getTextContent()));
    assertEquals(HEIGHT, Integer.parseInt(tileHeight.getTextContent()));

    NodeList fileNames = screenshot.getElementsByTagName("absolute_file_name");

    assertEquals(12, fileNames.getLength());
    String fourthFile = fileNames.item(4).getTextContent();
    OldApiBandaid.assertMatchesRegex(
        "The x coordinate should be before y coordinate",
        ".*baz_2_3",
        fileNames.item(11).getTextContent());

    OldApiBandaid.assertMatchesRegex(".*baz_1_0", fourthFile);

    NodeList relativeFileNames = screenshot.getElementsByTagName("relative_file_name");

    assertEquals(12, relativeFileNames.getLength());
    String relativeFourthFile = relativeFileNames.item(4).getTextContent();
    assertEquals("baz_1_0", relativeFourthFile);
  }
}
