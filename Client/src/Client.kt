import java.net.Socket

class Client(
    val host: String,
    val port : Int
) {
    private val socket: Socket
    private val communicator: SocketIO

    init{
        socket=Socket(host,port)
        communicator=SocketIO(socket)
    }

    /**
     * Метод остановки взаимодействия с сервером
     */
    fun stop(){
        communicator.stop()
    }

    /**
     * Запуск клиента и старт потока взаимодействия с сервером
     * Подписка на события получения информации
     */
    fun start(){
        communicator.startDataReceiving()
        communicator.addDataListener {
            //1;1;[3.0, 5.0, 3.0];[6.0, 8.0, 9.0];0
            //1;2;[3.0, 5.0, 3.0];[0.0, 0.0, 3.0];0
            val list = it.split(";")
            val values = list[2].split('[',',',']')
            val values2 = list[3].split('[',',',']')
            var res=0.0
            for (i in 1..values.size-2){
                res += values[i].toDouble()*values2[i].toDouble()
            }
            val iter = list[4].toInt()+1
            send(list[0]+";"+list[1]+";"+list[2]+";"+list[3]+";"+res+";"+iter)
        }
    }

    /**
     * Функция отправки информации серверу
     */
    fun send(data: String) {
        communicator.sendData(data)
    }
}