package dungpipeaddon;

public class ServerProxy implements IProxy {
    @Override
    public void preInit() {
        // No client-only registrations on the server.
    }
}
