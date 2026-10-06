package ru.homebooks.library;
import org.junit.Test;
import static org.junit.Assert.*;
public class CoverCropTest {
    @Test public void allowsFullRectangleAndPerspective(){assertTrue(CoverCrop.valid(CoverCrop.full()));assertTrue(CoverCrop.valid(new float[]{.2f,.1f,.9f,.2f,.8f,.9f,.1f,.8f}));}
    @Test public void rejectsCrossedCollapsedOrOutOfImage(){assertFalse(CoverCrop.valid(new float[]{0,0,1,1,1,0,0,1}));assertFalse(CoverCrop.valid(new float[]{0,0,1,0,1,0,0,0}));assertFalse(CoverCrop.valid(new float[]{-.1f,0,1,0,1,1,0,1}));assertFalse(CoverCrop.valid(new float[]{Float.NaN,0,1,0,1,1,0,1}));}
}
