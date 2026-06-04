package it.pruefert.headvault.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadJsonTest {

    private static final String SAMPLE = """
            [
              {"name":"Acacia Bark","uuid":"10c188cd-028f-49a5-9e4b-ed62ceac140c","value":"dmFsdWU=","tags":"Wood,Vanilla Block"},
              {"name":"No Tags","uuid":"2b1d3a44-0001-4ccc-8aaa-100000000001","value":"dmFsdWU="}
            ]
            """;

    @Test
    void parsesArrayAndSplitsTags() {
        List<Head> heads = HeadJson.parseArray(SAMPLE);
        assertEquals(2, heads.size());
        assertEquals("Acacia Bark", heads.get(0).name());
        assertEquals(List.of("Wood", "Vanilla Block"), heads.get(0).tags());
        assertTrue(heads.get(1).tags().isEmpty(), "missing tags -> empty list");
    }

    @Test
    void skipsRecordsWithoutUuidOrValue() {
        String json = """
                [
                  {"name":"good","uuid":"10c188cd-028f-49a5-9e4b-ed62ceac140c","value":"v"},
                  {"name":"no uuid","value":"v"},
                  {"name":"bad uuid","uuid":"not-a-uuid","value":"v"},
                  {"name":"no value","uuid":"2b1d3a44-0001-4ccc-8aaa-100000000001"}
                ]
                """;
        assertEquals(1, HeadJson.parseArray(json).size());
    }

    @Test
    void nonArrayThrows() {
        assertThrows(IllegalArgumentException.class, () -> HeadJson.parseArray("\"just a string\""));
    }

    @Test
    void parsesWrappedV2Response() {
        String wrapped = """
                {"status":"ok","data":[{"name":"A","uuid":"10c188cd-028f-49a5-9e4b-ed62ceac140c","value":"v","tags":"x"}]}
                """;
        List<Head> heads = HeadJson.parseArrayOrWrapped(wrapped);
        assertEquals(1, heads.size());
        assertEquals("A", heads.get(0).name());
    }

    @Test
    void toJsonRoundTrips() {
        List<Head> heads = HeadJson.parseArray(SAMPLE);
        List<Head> again = HeadJson.parseArray(HeadJson.toJson(heads));
        assertEquals(heads, again);
    }
}
