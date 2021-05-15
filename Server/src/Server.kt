import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.sql.*
import kotlin.concurrent.thread

class Server(val port : Int=5804) {

    private val sSocket: ServerSocket
    private val clients= mutableListOf<Client>()
    private var stop=false

    /** Вложенный класс клиентов, чтобы сервер хранил 'копии' подключенных клиентов
     * и взаимодействовал с подключенными клиентами
     * @param socket - сокет подключенного клиента
     */
    inner class Client(val socket: Socket){
        private var sock:SocketIO?=null

        /**
         * Обработка информации с подключенными клиентами
         */
        fun startDialog(){
            sock=SocketIO(socket).apply{
                addSocketClosedListener {
                    clients.remove(this@Client)
                }
                addDataListener{
                    clients.forEach{ client ->
                        if(client!=this@Client) client.sock?.sendData(this@Client.toString()+" :"+it)
                        else{
                            client.sock?.sendData("You said: "+it)
                        }
                    }
                }
                startDataReceiving()
            }
        }

        /**
         * Остановка всех подключений
         */
        fun stop(){
            sock?.stop()
        }
    }

    inner class DBHelper(
        val dbName : String,
        val address : String = "localhost",
        val port : Int = 3306,
        val user : String = "root",
        val password : String = "root"
    ){
        private var connection : Connection?=null
        private var statement: Statement? = null

        /**
         * Метод создания базы данных с указанием файла, где есть sql-запросы
         * @param userdata - имя файла с запросами бд
         */
        fun createDatabase(userdata : String){
            connect()
            dropAllTables()
            createTablesFromDump(userdata)
        }

        /**
         * Метод подключения к бд  $dbName
         * Обращение к субд через statement (sql запросы)
         */
        private fun connect(){
            statement?.run{
                if (!isClosed) close()
            }
            var rep = 0
            do {
                try {
                    connection =
                        DriverManager.getConnection("jdbc:mysql://$address:$port/$dbName?serverTimezone=UTC",
                            user,
                            password
                        )
                    statement =
                        DriverManager.getConnection("jdbc:mysql://$address:$port/$dbName?serverTimezone=UTC",
                            user,
                            password
                        ).createStatement()
                } catch (e: SQLSyntaxErrorException) {
                    println("Ошибка подключения к бд ${dbName} : \n${e.toString()}")
                    println("Попытка создания бд ${dbName}")
                    val tstmt =
                        DriverManager.getConnection("jdbc:mysql://$address:$port/?serverTimezone=UTC", user, password)
                            .createStatement()
                    tstmt.execute("CREATE SCHEMA `$dbName`")
                    tstmt.closeOnCompletion()
                    rep++
                }
            } while (statement == null && rep < 2)
        }

        /**
         * Метод для удаления таблиц,если они есть в бд
         */
        private fun dropAllTables(){
            println("Удаление всех таблиц в базе данных...")
            statement?.execute("DROP TABLE if exists `data`")
            println("Все таблицы удалены.")
        }

        /**
         * Создание таблиц через готовые sql запросы в файле
         * @param userdata - название файла
         */
        private fun createTablesFromDump(userdata : String){
            println("Создание структуры базы данных из дампа...")
            try {
                var query = ""
                File(userdata).forEachLine {
                    if(!it.startsWith("--") && it.isNotEmpty()){
                        query += it
                        if (it.endsWith(';')) {
                            statement?.addBatch(query)
                            query = ""
                        }
                    }
                }
                statement?.executeBatch()
                println("Структура базы данных успешно создана.")
            }
            catch (e: SQLException){
                println(e.message)
            }
            catch (e: Exception){
                println(e.message)
            }
        }

        /**
         * Метод закрытия подключения.утверждения
         */
        fun disconnect() {
            statement?.close()
        }

        /**
         * Метод заполнения таблицы через insert into, в каждое поле кортежа записываются данные
         * в формате "$name", где name - значение типа string, СУБД преобразует varchar в нужные форматы полей
         * @param userdata - название таблицы
         */
        fun fillTableFromCsv(userdata : String){
            val validData = userdata.split(".csv")
            println("Заполнение данными таблицы ${validData[0]}...")
            try{
                val bufferedData = File("data/"+validData[0]+".csv").bufferedReader()
                val requestTemplate = "INSERT INTO `${validData[0]}`" +
                        "("+ getDataFromTable(validData[0]) +") VALUES "
                while(bufferedData.ready()){
                    var request = "$requestTemplate("
                    val data = bufferedData.readLine().split(';')
                    data.forEachIndexed { i, name ->
                        request += "\"$name\""
                        if(i<data.size -1) request+=','
                    }
                    request+=')'
                    statement?.addBatch(request)
                }
                statement?.executeBatch()
                statement?.clearBatch()
                println("Таблица ${validData[0]} успешно заполнена!")
            } catch(e: Exception){
                println(e.toString())
            }

        }
        /**
         * Метод выдает данные из таблиц в субд (реализованно название атрибутов таблицы, закоментирован код для доставания типов атрибутов таблицы)
         * @param userdata название таблицы
         */
        private fun getDataFromTable(userdata : String): String{
            val query = "SHOW COLUMNS FROM `${userdata}`"
            val resultSet = statement?.executeQuery(query)
            var resultData = ""
            if (resultSet != null){
                //get column name
                while (resultSet.next()){
                    resultData+= "`"+resultSet.getString(1)+"` ,"
                }
                resultData= resultData.substring(0,(resultData.length)-2)
                /*
                if(columnName){
                    //get column name
                    while (resultSet.next()){
                        resultData+= "`"+resultSet.getString(1)+"` ,"
                    }
                    resultData= resultData.substring(0,(resultData.length)-4)
                }
                else {
                    //get type of column ( Not Worked now )
                    while (resultSet.next()){
                        val currentField = resultSet.getString(1)
                        val validField = currentField.split("(")
                        println(validField)
                        println(map.get(validField[0]))
                        resultData+= map.get(validField[0])
                    }
                }
                 */
            }
            return resultData
        }

        fun getQuery(sql : String){
            val rs =statement?.executeQuery(sql)
            while(rs?.next()==true){
                //сбор в матрицу
                println(rs.getString(1)+";"+rs.getString(2)+";"+rs.getString(3)+";"+rs.getString(4))
            }
        }

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
     * Старт сервера
     * Сервер постоянно ждет новых подключений к нему
     */
    fun start(){
        stop=false
        thread{
            try{
                while(!stop){
                    clients.add(Client(sSocket.accept()).also{client->client.startDialog()})
                }
            }catch (e: Exception){
                println("${e.message}")
            }
            finally {
                stopAllClient()
                sSocket.close()
                println("Сервер остановлен")
            }

        }
    }
}