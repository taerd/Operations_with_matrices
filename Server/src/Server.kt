import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.ServerSocket
import java.net.Socket


class Server(val port : Int=5804) {

    private val sSocket: ServerSocket
    private val clients= mutableListOf<Client>()
    private val maxClients = 24
    private val availableClients= Channel<Client>(maxClients)
    private var resultMatrix=mutableListOf<ArrayList<Float>>()
    private var stop=false
    private var calculating=false

    private var table= ""
    private lateinit var dataBase : DBHelper

    /** Вложенный класс клиентов, чтобы сервер хранил 'копии' подключенных клиентов
     * и взаимодействовал с подключенными клиентами
     * @param socket - сокет подключенного клиента
     */
    inner class Client(val socket: Socket){
        private var sock:SocketIO?=null

        /**
         * Обработка информации с подключенными клиентами
         */
        suspend fun startDialog(){
            sock=SocketIO(socket).apply{
                addSocketClosedListener {
                    clients.remove(this@Client)
                }

                addDataListener{
                    val list = it.split(";")
                    //println(it)
                    //Проверка
                    //1;1;[3.0, 5.0, 3.0];[6.0, 8.0, 9.0];result;iter
                    if(list[5].toInt()==1){
                        resultMatrix[list[0].toInt()-1][list[1].toInt()-1]=list[4].toFloat()

                        CoroutineScope(Dispatchers.Default).launch{
                            availableClients.send(this@Client)

                            var testClient = getAvailableClient().await()
                            //Проверка другим клиентом значения
                            while(testClient==this@Client) {
                                availableClients.send(testClient)
                                delay(10)
                                testClient = getAvailableClient().await()
                            }
                            testClient.sendData(list[0]+";"+list[1]+";"+list[2]+";"+list[3]+";"+"1")
                        }
                    }
                    else{
                        if(list[5].toInt()==2){
                            //если значения не равны, то второе значение нигде не сохраняется
                                // Доверяюсь тому, что третий раз точно правильно посчитает
                                    // Может попастся тот же клиент, что и считал в 1 раз
                            if(resultMatrix[list[0].toInt()-1][list[1].toInt()-1]!=list[4].toFloat()){

                                CoroutineScope(Dispatchers.Default).launch{
                                    availableClients.send(this@Client)
                                    val testClient = getAvailableClient().await()
                                    testClient.sendData(list[0]+";"+list[1]+";"+list[2]+";"+list[3]+";"+"2")
                                }
                            }else{
                                CoroutineScope(Dispatchers.Default).launch{
                                availableClients.send(this@Client)
                                }
                            }
                        }
                        //считаю что в 3 раз получаем уже правильное значение
                        if(list[5].toInt()==3){
                            CoroutineScope(Dispatchers.Default).launch{
                                availableClients.send(this@Client)
                            }
                            resultMatrix[list[0].toInt()-1][list[1].toInt()-1]=list[4].toFloat()
                        }
                    }
                }
                startDataReceiving()
            }
        }

        /**
         * Функция отправки информации
         * data- информация
         */
        fun sendData(data : String){
            this.sock?.sendData(data)
        }

        /**
         * Остановка всех подключений
         */
        fun stop(){
            sock?.stop()
        }
        init{}
    }

    init{
        sSocket= ServerSocket(port)
    }

    /**
     * Закрытие сокета сервера
     */
    fun stop(){
        sSocket.close()
        stop=true
    }

    /**
     * Остановка всех клиентов
     */
    private fun stopAllClient(){
        clients.forEach{it.stop()}
    }

    /**
     * Не блокирует основной процесс а выполняется отдельно, не требует создание потока
     * @param table - название таблицы, где лежат матрицы для вычислений
     * @param dataBase - название базы данных, где лежит таблица
     */
    private fun clientWait()=CoroutineScope(Dispatchers.Default).launch {
        try {
            while (!stop) {
                val sock=sSocket.accept()
                val Client = Client(sock)
                Client.startDialog()
                clients.add(Client)
                availableClients.send(Client)
                //Когда клиентов больше двух, можно начать вычисления
                if(clients.count()>=2){
                    Calculate()
                }
            }
        } catch (e: Exception) {
            println("${e.message}")
        } finally {
            stopAllClient()
            sSocket.close()
            println("Сервер остановлен")
        }
    }
    /**
     * Старт сервера
     * Сервер постоянно ждет новых подключений к нему
     * @param table - название таблицы с матрицами
     * @param dataBase - База данных,где лежит таблица
     */
    fun start(tbl : String,db : DBHelper){
        stop=false
        table=tbl
        dataBase = db
        clientWait()
    }

    /**
     * Коррутинная функция для получения свободных клиентов
     */
    private fun getAvailableClient()=CoroutineScope(Dispatchers.Default).async{
        val avC = availableClients.receive()
        avC
    }


    private var next= false
    /**
     * Вычисления матриц
     * @param table - название таблицы в которой лежат матрицы
     * @param dataBase - название базы данных в которой лежит таблица
     */
    private fun Calculate(){
        //Если вычисления начались, то нет смысла делать их еще раз
        if (!calculating){
            println("Starting calculate")
            calculating=true

            CoroutineScope(Dispatchers.Default).launch{

                val matrixCount = dataBase.getDataFromTable(table,"Max(`id`)","`row`",1)[0].toInt()
                for (i in 1..(matrixCount-1)){

                    println("Multiply ${i} and ${i+1}")
                    next=false
                    //Перемножение всех таблиц в базе данных
                    Multiply(i,i+1)

                    // Отправка полученной матрицы базе данных
                    CoroutineScope(Dispatchers.Default).launch {
                        //проверка конца вычислений
                        //по количеству свободных клиентов - должно совпасть с количеством клиентов
                        while(availableClients.toString().split("size=")[1].split(')')[0].toInt()!=clients.size){
                            delay(500)
                        }

                        //Заполнение бдшки значениями resultMatrix
                        val currentMatrix= matrixCount+i
                        val queryTemplate = "INSERT INTO `${table}` VALUES"
                        var query=queryTemplate
                        for(j in 1..resultMatrix.size){
                            for( k in 1.. resultMatrix[j-1].size){
                                val value = resultMatrix[j-1][k-1]
                                query += "("+currentMatrix+","+j+","+k+","+value+"),"
                            }
                        }
                        resultMatrix
                        query=query.substring(0,query.length-1)
                        dataBase.ExecuteQueryWithoutCheck(query)
                        println("Success  with ${i} * ${i+1} at ${currentMatrix}")
                        next=true
                    }

                    //Ожидание пока матрица отправится бдшке
                    while(!next){
                        delay(100)
                    }

                }
            }
        }
    }


    /**
     * Приостанавливаемая функция перемножения матриц
     * @param table - таблица с матрицами
     * @param dataBase - База данных с матрицами
     * @param first - первая матрица
     * @param second - вторая матрица
     */
    suspend fun Multiply(first : Int, second : Int){

        //Доставать "value" так не правильно.. можно узнать как называются поля table
        val mtr1list = dataBase.getDataFromTable(table,"value","id",first)
        val row1mtr = dataBase.getDataFromTable(table,"Max(`row`)","id",first)[0].toInt()
        val col1mtr = mtr1list.count()/row1mtr

        //заполнение первой матрицы значениями из бд
        val matrix1= mutableListOf<List<Float>>()
        for (i in 0..row1mtr-1 ){
            val vector= mutableListOf<Float>()
            for ( j in 0..col1mtr-1){
                vector.add(mtr1list[col1mtr*i+j])
            }
            matrix1.add(vector)

        }

        val mtr2list = dataBase.getDataFromTable(table,"value","id",second)
        val row2mtr = dataBase.getDataFromTable(table,"Max(`row`)","id",second)[0].toInt()
        val col2mtr = mtr2list.count()/row2mtr

        val matrix2= mutableListOf<List<Float>>()
        for (i in 0..row2mtr-1 ){
            val vector= mutableListOf<Float>()
            for ( j in 0..col2mtr-1){
                //сразу транспонированная
                vector.add(mtr2list[row1mtr*j+i])
            }
            matrix2.add(vector)
        }

        //заполнение результирующей матрицы нулями
        //нужна для хранения значений
        resultMatrix.clear()
        for(i in 1..row1mtr){
            val vector= mutableListOf<Float>()
            for (j in 1..col2mtr){
                vector.add(0.0F)
            }
            resultMatrix.add(ArrayList(vector))
        }

        //проверка на возможность умножения размеров матриц
        if (col1mtr==row2mtr){
            for( i in 1..col1mtr){
                for( j in 1..row2mtr){
                    val availableClient=getAvailableClient().await()
                    availableClient.sendData(i.toString()+";"+j.toString()+";"+matrix1[i-1]+";"+matrix2[j-1]+";"+"0")
                }
            }
        }

    }
}