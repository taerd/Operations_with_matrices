import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Класс посредник - передатчик информации между подключенными
 */
class SocketIO(val socket : Socket) {
    private var stop = false

    //Событие, которое возникает при закрытии подключения
    private val socketClosedListener = mutableListOf<()->Unit>()
    fun addSocketClosedListener(l:()->Unit){
        socketClosedListener.add(l)
    }
    fun removeSocketClosedListener(l:()->Unit){
        socketClosedListener.remove(l)
    }

    //Событие которое возникает при получении информации от сокета
    private val DataListener = mutableListOf<(String)->Unit>()
    fun addDataListener(l:(String)->Unit){
        DataListener.add(l)
    }
    fun removeDataListener(l:(String)->Unit){
        DataListener.remove(l)
    }

    /**
     * Принудительный разрыв соедения
     * Закрытие сокетов
     */
    fun stop(){
        stop=true
        socket.close()
    }

    /**
     * Функция обработки полученной информации
     */
    fun startDataReceiving() {
        stop=false
        thread{
            try {
                val br = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (!stop) {
                    val data = br.readLine()
                    if(data!=null) {
                        DataListener.forEach { it(data) }
                    }else{
                        throw IOException("Связь прервалась")
                    }
                }
            }catch (ex:Exception){
                println(ex.message)
            }
            finally{
                socket.close()
                socketClosedListener.forEach { it() }
            }
        }
    }

    /**
     * Отправка данных подключению
     * @param data - сообщение/строка
     */
    fun sendData(data:String):Boolean{
        try {
            val pw = PrintWriter(socket.getOutputStream())
            pw.println(data)
            pw.flush()
            return true
        }catch (ex:Exception){
            return false
        }
    }
}