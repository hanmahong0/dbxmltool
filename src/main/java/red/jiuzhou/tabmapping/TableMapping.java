package red.jiuzhou.tabmapping;

import java.util.Set;
import java.util.HashSet;

public class TableMapping {
    public String svr_tab;
    public String clt_tab;
    public String same_fileds;
    public String svr_redundant_fields;
    public String clt_redundant_fields;

    public Set<String> getSameFieldsSet() {
        Set<String> set = new HashSet<>();
        if (same_fileds != null && !same_fileds.trim().isEmpty()) {
            for (String field : same_fileds.split("\\s*,\\s*")) {
                set.add(field.trim());
            }
        }
        return set;
    }

}
