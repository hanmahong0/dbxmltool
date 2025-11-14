package red.jiuzhou.ai;

public class AiModelFactory {
    public static AiModelClient getClient(String modelName) {
        switch (modelName.toLowerCase()) {
            case "qwen": return new TongYiClient();
            case "doubao": return new DoubaoClient();
            case "kimi": return new KimiClient();
            case "deepseek": return new DeepSeekClient();
            default: throw new IllegalArgumentException("不支持的模型类型：" + modelName);
        }
    }
}