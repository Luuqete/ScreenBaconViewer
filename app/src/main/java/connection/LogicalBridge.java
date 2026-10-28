package connection;

import protocol.Protocol;
import java.util.function.Consumer;
import idsParser.IdsParser;

public abstract class LogicalBridge {
    public abstract void onComunicationFound(Protocol server);

    public void saveId(String id) {
        new Thread(() -> IdsParser.saveId(id)).start();
    }

    public boolean isIdSaved(String id, Consumer<Boolean> callback) {
        return IdsParser.isIdSaved(id);
    }
}
