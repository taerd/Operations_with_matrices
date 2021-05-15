import java.net.Socket

class Client(
    val host: String,
    val port : Int
) {
    private val socket: Socket
    private val communicator: SocketIO

    public fun addSessionFinishedListener(l:()->Unit){
        communicator.addSocketClosedListener(l)
    }
    public fun removeSessionFinishedListener(l:()->Unit){
        communicator.removeSocketClosedListener(l)
    }

    init{
        socket=Socket(host,port)
        communicator=SocketIO(socket)
    }
    fun stop(){
        communicator.stop()
    }

    fun start(){
        communicator.startDataReceiving()
        communicator.addDataListener {
            println(it)//обработчик события в форму -окошко
        }
    }

    fun send(data: String) {
        communicator.sendData(data)
    }
}