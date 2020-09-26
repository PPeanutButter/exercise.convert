# 这个不需要图片的,且只有选择题
import base64
import json
import sqlite3
from bs4 import BeautifulSoup

# 打开anki数据库
conn = sqlite3.connect('xutao/collection.anki2')
# 新建我的数据库
new_conn = sqlite3.connect("xutao/2021-youtiku.db")

with open("schema.sql", mode='r', encoding='utf-8') as f:
    new_conn.cursor().executescript(f.read())
    f.close()
    new_conn.commit()
# 保存一些必要信息
new_conn.execute("PRAGMA user_version=1")

c = conn.cursor()
# 查询所有题目
cursor = c.execute("select flds,sfld from notes order by sfld")
# 他的分隔符,应该是anki统一的分隔符
fenge = chr(31)
chap = []
for row in cursor:
    # 支持章节
    sfld = row[1]
    chapName = sfld[4:6]
    flds = row[0]
    contents = flds.split(fenge)
    # print(flds.split(fenge))
    try:
        chap_id = chap.index(chapName)
    except ValueError:
        chap.append(chapName)
        chap_id = chap.index(chapName)
    # 需要去掉前面的<dan>单</dan>
    topic_start = contents[1].find(">", contents[1].find(">")+1)+1
    topic_end = contents[1].find("<", topic_start)
    topic = contents[1][topic_start:topic_end]
    topic = base64.b64encode(topic.encode()).decode()
    # print(topic)
    # 选项与答案
    options = contents[2].split("|")
    ans_start = 65
    ans = ""
    for i in range(len(options)):
        if options[i].strip().startswith("+"):
            ans = ans + chr(ans_start + i)
            options[i] = options[i].strip()[1:]
    options = base64.b64encode(json.dumps(options, ensure_ascii=False).encode()).decode()
    # print(ans)
    if ans == "":
        print("跳过一个无法解析到答案的题目")
        continue
    # 去除解析中的无用标签
    soup = BeautifulSoup(contents[5]+"<br/><br/>"+contents[6], features="lxml")
    [s.extract() for s in soup('span')]
    # print(soup)
    explain = base64.b64encode(soup.encode()).decode()
    # print(explain)
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
new_conn.cursor().execute("update Chapter set name = '马克思主义基本原理概论' where name = '马原';")
new_conn.cursor().execute("update Chapter set name = '思想道德修养与法律基础' where name = '思修';")
new_conn.cursor().execute("update Chapter set name = '毛泽东思想和中国特色社会主义理论体系概论' where name = '毛概';")
new_conn.cursor().execute("update Chapter set name = '中国近现代史纲要' where name = '史纲';")
new_conn.commit()
print("转化完成")
