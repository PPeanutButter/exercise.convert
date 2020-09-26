# 这个不需要图片的,且只有选择题
import base64
import json
import sqlite3

# 打开anki数据库
conn = sqlite3.connect('1k/collection.anki2')
# 新建我的数据库
new_conn = sqlite3.connect("1k/xiao1k.db")

with open("schema.sql", mode='r', encoding='utf-8') as f:
    new_conn.cursor().executescript(f.read())
    f.close()
    new_conn.commit()
# 保存一些必要信息
new_conn.execute("PRAGMA user_version=1")

c = conn.cursor()
# 删除第一行的非题目信息
c.execute("delete from notes where id=1561452862848")
conn.commit()
# 查询所有题目
cursor = c.execute("select flds,sfld from notes order by sfld")
# 他的分隔符
fenge = chr(31)
chap = []
for row in cursor:
    # 支持章节
    sfld = row[1]
    chapName = sfld[sfld.find(' ') + 1:sfld.find('/')]
    try:
        chap_id = chap.index(chapName)
    except ValueError:
        chap.append(chapName)
        chap_id = chap.index(chapName)
    # 经典字段
    flds = row[0]
    contents = flds.split(fenge)
    topic = base64.b64encode(contents[1].encode()).decode()
    options = contents[2].split("|")
    # print(topic)
    ans_start = 65
    ans = ""
    for i in range(len(options)):
        if options[i].strip().startswith("+"):
            ans = ans + chr(ans_start + i)
            options[i] = options[i].strip()[1:]
    options = base64.b64encode(json.dumps(options, ensure_ascii=False).encode()).decode()
    explain = base64.b64encode((contents[7] + "<br/><br/>【位置】" + contents[8]).encode()).decode()
    # 读取完毕-保存到我的数据库中
    if ans == "":
        print("跳过一个无法解析到答案的题目")
        continue
    new_conn.cursor().execute("INSERT INTO " + ("DX" if len(ans) == 1 else "DD") + "(Topic,OptionList,Result,"
                                                                                   "Explain,chapId)VALUES(?, ?, ?, ?, "
                                                                                   "?);",
                              (topic, options, ans, explain, chap_id))
    new_conn.commit()
# 保存章节名字
for idx, val in enumerate(chap):
    new_conn.cursor().execute("INSERT INTO Chapter(chapId,name)VALUES(?, ?);",
                              (idx, val))
    new_conn.commit()
print("转化完成")
