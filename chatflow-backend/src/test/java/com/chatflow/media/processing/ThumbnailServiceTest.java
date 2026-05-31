package com.chatflow.media.processing;

import com.chatflow.media.entity.MessageType;
import com.chatflow.media.storage.MediaStorageService;
import com.chatflow.media.storage.StoredMedia;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Phase 6 verification — real image in, real thumbnail out. No Spring context.
 */
class ThumbnailServiceTest {

    private final MediaStorageService storage = mock(MediaStorageService.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private ThumbnailService newService(int maxSize) {
        ThumbnailService service = new ThumbnailService(storage, publisher);
        ReflectionTestUtils.setField(service, "maxSize", maxSize);
        ReflectionTestUtils.setField(service, "ffmpegPath", "ffmpeg");
        ReflectionTestUtils.setField(service, "ffmpegTimeoutSeconds", 20L);
        return service;
    }

    @Test
    void generatesDownsizedJpegThumbnailForImage() throws Exception {
        ThumbnailService service = newService(320);
        byte[] sourcePng = pngImage(1000, 800);

        when(storage.storeBytes(any(), anyString(), eq("image/jpeg")))
                .thenReturn(StoredMedia.builder()
                        .storageKey("thumbnails/image/2026/05/abc_thumb.jpg")
                        .publicUrl("http://localhost:8080/media/thumbnails/image/2026/05/abc_thumb.jpg")
                        .build());

        UUID mediaId = UUID.randomUUID();
        service.onMediaProcessing(new MediaProcessingEvent(
                this, mediaId, "image/2026/05/abc.png", MessageType.IMAGE, "image/png", sourcePng));

        // storeBytes was called with valid, downsized JPEG bytes
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).storeBytes(bytes.capture(), key.capture(), eq("image/jpeg"));

        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(bytes.getValue()));
        assertThat(thumb).as("thumbnail decodes as an image").isNotNull();
        assertThat(thumb.getWidth()).isLessThanOrEqualTo(320);
        assertThat(thumb.getHeight()).isLessThanOrEqualTo(320);
        // 1000x800 scaled into a 320 box → width is the limiting edge
        assertThat(thumb.getWidth()).isEqualTo(320);
        assertThat(key.getValue()).startsWith("thumbnails/").endsWith("_thumb.jpg");

        // and a ThumbnailGeneratedEvent was published with the stored URL
        ArgumentCaptor<ThumbnailGeneratedEvent> event =
                ArgumentCaptor.forClass(ThumbnailGeneratedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().getMediaMessageId()).isEqualTo(mediaId);
        assertThat(event.getValue().getThumbnailUrl()).contains("_thumb.jpg");
    }

    @Test
    void skipsThumbnailForAudioAndFile() {
        ThumbnailService service = newService(320);

        service.onMediaProcessing(new MediaProcessingEvent(
                this, UUID.randomUUID(), "audio/2026/05/a.mp3",
                MessageType.AUDIO, "audio/mpeg", new byte[]{1, 2, 3}));

        verifyNoInteractions(storage);
        verifyNoInteractions(publisher);
    }

    @Test
    void corruptImageDoesNotThrowAndPublishesNothing() {
        ThumbnailService service = newService(320);

        // Not a real image — generation fails, fallback leaves thumbnail null
        service.onMediaProcessing(new MediaProcessingEvent(
                this, UUID.randomUUID(), "image/2026/05/bad.png",
                MessageType.IMAGE, "image/png", "not-an-image".getBytes()));

        verifyNoInteractions(publisher);
        verify(storage, never()).storeBytes(any(), anyString(), anyString());
    }

    private byte[] pngImage(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
