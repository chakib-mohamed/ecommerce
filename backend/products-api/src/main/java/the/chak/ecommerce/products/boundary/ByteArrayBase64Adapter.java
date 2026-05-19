package the.chak.ecommerce.products.boundary;

import java.util.Base64;
import jakarta.json.bind.adapter.JsonbAdapter;

public class ByteArrayBase64Adapter implements JsonbAdapter<byte[], String> {

    @Override
    public String adaptToJson(byte[] obj) throws Exception {
        if (obj == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(obj);
    }

    @Override
    public byte[] adaptFromJson(String obj) throws Exception {
        if (obj == null) {
            return null;
        }
        return Base64.getDecoder().decode(obj);
    }
}
