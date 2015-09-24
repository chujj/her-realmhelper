# her-realmhelper

为工作中使用[Realm-0.80.0](https://github.com/realm/realm-java)数据库时制作的
工具插件。仿照
[ButterKnife](https://github.com/JakeWharton/butterknife)使用java
annotation标记程序中处理点。

主要功能有：

* 网络得到的databean映射到本地数据库中
    * 根据annotation生成src(json bean)到target(realmObject)的帮助类，
      并生成static setter方法。
    * 提供BatchSetter.batchSet()方法，将json bean能够递归成员，一并
      cast到平坦的realmObject中
* 生成重建Realm数据表帮助类
    * 根据annotation生成 realm清理数据表，重建数据表的静态方法。当删除
      客户端本地以cache为目的建立数据表时，能够在数据表升级后，直接重
      建，忽略掉column的变化。

## Installation

* 在eclipse中打开annotation processor，指定导出后的jar即可

* 以gradle引用

``
    repositories {
        // ...
        maven { url "https://jitpack.io" }
    }


    dependencies {
        compile 'com.github.chujj:her-realmhelper:[Tag/commit hash/-SNAPSHOT]'
    }
``


## Usage

数据映射功能:
``
    @FieldToSet
    @GenerateClassSetter
	BatchSetter.batchSetter()
``
Realm数据表升级相关:
``
    @PrimaryKeyMark
    @GenerateRealmTableHelper
``



