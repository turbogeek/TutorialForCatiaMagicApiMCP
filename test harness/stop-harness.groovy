// Cleanly shut down the test harness.
//
// Prefers a loopback HTTP call (which lets the server close gracefully after
// finishing the response). Falls back to enumerating the harness's server
// object if that was exported onto the Binding during bootstrap.

import com.nomagic.magicdraw.core.Application
import java.net.HttpURLConnection
import java.net.URL

int port = System.getProperty('harness.port', '8765') as int
String base = 'http://127.0.0.1:' + port

try {
    HttpURLConnection c = (HttpURLConnection) new URL(base + '/stop-harness').openConnection()
    c.requestMethod = 'POST'
    c.doOutput = true
    c.outputStream.write('{}'.getBytes('UTF-8'))
    c.outputStream.close()
    int code = c.responseCode
    String body = code < 400 ? c.inputStream.text : (c.errorStream?.text ?: '')
    Application.getInstance().getGUILog().log(
        'Test harness stop: HTTP ' + code + ' ' + body
    )
} catch (Throwable t) {
    Application.getInstance().getGUILog().showError(
        'Failed to stop harness at ' + base + ' : ' + t.getMessage() +
        '\n(If the harness is not running, this is expected.)'
    )
}
