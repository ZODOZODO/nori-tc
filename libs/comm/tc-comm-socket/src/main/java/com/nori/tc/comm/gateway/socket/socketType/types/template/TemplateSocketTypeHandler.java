package com.nori.tc.comm.gateway.socket.socketType.types.template;

import com.nori.tc.comm.core.buffer.ReassemblyBuffer;
import com.nori.tc.comm.gateway.socket.frame.SocketFrame;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeDecodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeEncodeResult;
import com.nori.tc.comm.gateway.socket.socketType.core.SocketTypeHandler;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Copy-ready, single-file template socketType handler.
 *
 * <p>Copy procedure:
 * - Copy {@code types/template} to a new type directory.
 * - Rename this class and replace {@link #SOCKET_TYPE} with the real DB value.
 * - Implement frame extraction, decode, and encode rules.
 * - Register the new handler in {@code GatewayCommConfiguration}.</p>
 *
 * <p>Design note:
 * - This template intentionally keeps all logic in one file so teams that prefer
 *   one-file-per-type can start quickly.
 * - If complexity grows, split the logic into extractor/decoder/encoder classes.</p>
 */
public final class TemplateSocketTypeHandler implements SocketTypeHandler {

    /**
     * Template socket type value.
     *
     * <p>IMPORTANT:
     * - Replace this with the real {@code tc_eqp_socket.socket_protocol_type} value
     *   before production usage.</p>
     */
    public static final String SOCKET_TYPE = "TEMPLATE_SOCKET_TYPE";

    private final Charset charset;

    /**
     * Creates a template handler using UTF-8.
     */
    public TemplateSocketTypeHandler() {
        this(StandardCharsets.UTF_8);
    }

    /**
     * Creates a template handler with an explicit charset.
     *
     * @param charset charset used by decode/encode logic
     */
    public TemplateSocketTypeHandler(final Charset charset) {
        this.charset = (charset == null) ? StandardCharsets.UTF_8 : charset;
    }

    /**
     * Returns this handler's socket type identifier.
     *
     * @return socket type name
     */
    @Override
    public String socketType() {
        return SOCKET_TYPE;
    }

    /**
     * Extracts one frame from the reassembly buffer.
     *
     * <p>Template default:
     * - Uses LF ({@code '\n'}) as frame delimiter.
     * - Returns {@code null} when no complete frame exists yet.</p>
     *
     * @param buffer reassembly buffer
     * @param maxFrameBytes max frame safety limit
     * @return extracted frame or null
     */
    @Override
    public SocketFrame tryExtractOne(final ReassemblyBuffer buffer, final int maxFrameBytes) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer is null");
        }

        final int readable = buffer.readableBytes();
        if (readable == 0) {
            return null;
        }

        int delimiterIndex = -1;
        for (int i = 0; i < readable; i++) {
            if (buffer.get(i) == (byte) '\n') {
                delimiterIndex = i;
                break;
            }
        }

        if (delimiterIndex < 0) {
            return null;
        }

        final int frameLength = delimiterIndex + 1;
        if (frameLength > maxFrameBytes) {
            throw new IllegalStateException("Socket frame too large: " + frameLength + " > " + maxFrameBytes);
        }

        final byte[] frameBytes = buffer.copy(0, frameLength);
        buffer.discard(frameLength);
        return new SocketFrame(frameBytes, System.currentTimeMillis());
    }

    /**
     * Decodes one frame into the standardized decode model.
     *
     * <p>Template default:
     * - First token is messageName.
     * - Remaining text is body.</p>
     *
     * @param frameBytes extracted frame bytes
     * @return decode result
     */
    @Override
    public SocketTypeDecodeResult decode(final byte[] frameBytes) {
        if (frameBytes == null) {
            throw new IllegalArgumentException("frameBytes is null");
        }

        final String text = new String(frameBytes, charset)
                .replace("\r", "")
                .replace("\n", "")
                .trim();

        if (text.isBlank()) {
            throw new IllegalArgumentException("Empty template frame");
        }

        final String[] parts = text.split("\\s+", 2);
        final String messageName = parts[0];
        final String bodyText = (parts.length > 1) ? parts[1] : "";

        return new SocketTypeDecodeResult(
                messageName,
                Map.of("rawText", text),
                bodyText
        );
    }

    /**
     * Encodes one command object into outbound bytes.
     *
     * <p>Template default:
     * - Uses {@code command.toString()} as-is.
     * - Does not append or remove delimiter characters.</p>
     *
     * @param command outbound command object
     * @return encode result
     */
    @Override
    public SocketTypeEncodeResult encode(final Object command) {
        if (command == null) {
            throw new IllegalArgumentException("command is null");
        }

        final byte[] bytes = command.toString().getBytes(charset);
        return new SocketTypeEncodeResult(bytes, "template outbound pass-through encoding");
    }
}
