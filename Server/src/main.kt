fun main(){
    val s = Server()
    s.start()

    val db = s.DBHelper("matrix.sql")
    db.createDatabase("matrix.sql")

}