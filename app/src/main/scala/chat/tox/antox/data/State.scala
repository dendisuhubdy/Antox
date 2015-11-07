package chat.tox.antox.data

import android.app.Activity
import android.content.{Context, Intent}
import android.preference.PreferenceManager
import chat.tox.antox.activities.LoginActivity
import chat.tox.antox.av.CallManager
import chat.tox.antox.tox.{ToxService, ToxSingleton}
import chat.tox.antox.transfer.FileTransferManager
import chat.tox.antox.utils.AntoxNotificationManager
import chat.tox.antox.wrapper.ContactKey
import rx.lang.scala.subjects.BehaviorSubject

object State {

  private var _chatActive: Boolean = false
  private var _activeKey: Option[ContactKey] = None

  val chatActive = BehaviorSubject[Boolean](false)
  val chatActiveSubscription = chatActive.subscribe(x => State.setChatActive(x))
  val activeKey = BehaviorSubject[Option[ContactKey]](None)
  val activeKeySubscription = activeKey.subscribe(x => State.setActiveKey(x))
  val typing = BehaviorSubject[Boolean](false)

  val transfers: FileTransferManager = new FileTransferManager()

  var db: AntoxDB = _
  private var _userDb: Option[UserDB] = None
  val callManager = new CallManager()

  def userDb(context: Context): UserDB = {
    _userDb match {
      case Some(userDb) =>
        userDb
      case None =>
        val db = new UserDB(context)
        _userDb = Some(db)
        db
    }
  }

  def isChatActive(chatKey: ContactKey): Boolean = {
    _chatActive && _activeKey.contains(chatKey)
  }

  def setChatActive(b: Boolean): Unit = {
    _chatActive = b
  }

  private def setActiveKey(k: Option[ContactKey]): Unit = {
    require(k != null)
    _activeKey = k
  }

  def login(name: String, context: Context): Unit = {
    userDb(context).login(name)
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    if(preferences.getBoolean("notifications_persistent", false)){
      AntoxNotificationManager.createPersistentNotification(context)
    }
  }

  def logout(activity: Activity): Unit = {
    val preferences = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext)
    if(preferences.getBoolean("notifications_persistent", false)){
      AntoxNotificationManager.removePersistentNotification()
    }

    //clear notifications as they are now invalid after logging out
    AntoxNotificationManager.clearAllNotifications()

    if (!userDb(activity).getActiveUserDetails.loggingEnabled) {
      db.friendInfoList.toBlocking.first.foreach(f => db.deleteChatLogs(f.key))
    }

    //workaround for contacts appearing offline when the DB is upgraded
    db.synchroniseWithTox(ToxSingleton.tox)
    db.close()

    val startTox = new Intent(activity, classOf[ToxService])
    activity.stopService(startTox)
    userDb(activity).logout()
    val login = new Intent(activity, classOf[LoginActivity])
    activity.startActivity(login)
    activity.finish()
  }
}
