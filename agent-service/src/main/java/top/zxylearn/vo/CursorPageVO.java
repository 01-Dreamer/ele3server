package top.zxylearn.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CursorPageVO<T> {
    private List<T> records;
    private String nextCursor;
    private Boolean hasMore;
}
