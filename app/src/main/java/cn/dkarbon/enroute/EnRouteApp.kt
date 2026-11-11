package cn.dkarbon.enroute

import android.app.Application

/**
 * 进程级单例（暂时只用来占位 Application）。
 *
 * 以后要放全局的东西（WorkManager 的提醒调度、Glance 的更新时间戳）都从这里拿，
 * 别用静态 object 到处散。
 */
class EnRouteApp : Application()
