package com.contextclip.agent;

import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardImageDiagnosticTest {

    static class MixedTransferable implements Transferable {
        private final Image image;
        private final String text;

        MixedTransferable(Image image, String text) {
            this.image = image;
            this.text = text;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.stringFlavor, DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.stringFlavor.equals(flavor) || DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (DataFlavor.stringFlavor.equals(flavor)) return text;
            if (DataFlavor.imageFlavor.equals(flavor)) return image;
            throw new UnsupportedFlavorException(flavor);
        }
    }

    @Test
    void testBufferedImageCapture() {
        Clipboard clip = new Clipboard("diag-clip");
        BufferedImage bi = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        clip.setContents(new MixedTransferable(bi, null), null);

        ClipboardMonitor monitor = new ClipboardMonitor(clip, text -> {});
        String content = monitor.readClipboardTextSafe();
        assertNotNull(content, "Expected image content, but got null");
        assertTrue(content.startsWith("data:image/png;base64,"), "Expected data URL");
    }

    @Test
    void testImagePrecedenceOverStringWhenBothPresent() {
        Clipboard clip = new Clipboard("mixed-clip");
        BufferedImage bi = new BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB);
        // Simulates browser "Copy Image" where browser attaches image URL or alt-text as stringFlavor
        clip.setContents(new MixedTransferable(bi, "https://example.com/photo.png"), null);

        ClipboardMonitor monitor = new ClipboardMonitor(clip, text -> {});
        String content = monitor.readClipboardTextSafe();
        assertNotNull(content);
        assertTrue(content.startsWith("data:image/png;base64,"),
                "Image flavor must take precedence over string flavor when an image is copied");
    }

    @Test
    void testMonitorEmitsImageToCallback() {
        List<String> emitted = new ArrayList<>();
        Clipboard clip = new Clipboard("emit-clip");
        ClipboardMonitor monitor = new ClipboardMonitor(clip, emitted::add);
        monitor.start();

        BufferedImage bi = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        clip.setContents(new MixedTransferable(bi, null), null);
        monitor.processClipboardChange();

        assertEquals(1, emitted.size());
        assertTrue(emitted.get(0).startsWith("data:image/png;base64,"));
        monitor.stop();
    }
}
