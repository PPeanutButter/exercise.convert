# ① 大类题库目录：mediapi.medicool.cn/mediexam/Swcf/Calculator.svc/kulist
# ② 题库的章节目录：mediapi.medicool.cn/mediexam/Swcf/Calculator.svc/typezjlist?kuId=20，kulist里面的id
# ③ 题目：mediapi.medicool.cn/mediexam/Swcf/Calculator.svc/zjexamlist?CId=20&EIdlist=0%2C0%2C0&TId=13，
#           tid是②里面的id，cid是kulist里面的id，eidlist=0,0,0
import base64
import json
import os
import sqlite3
import urllib.request


def get_json(url, cache_name):
    print(url)
    if os.path.exists("cache/" + cache_name + ".json"):
        f = open("cache/" + cache_name + ".json", mode='r', encoding='UTF-8')
        ele_json = json.loads(f.read())
        f.close()
        print("cache")
        return ele_json
    resp = urllib.request.urlopen(url)
    r = resp.read()
    ele_json = json.loads(r)
    f1 = open("cache/" + cache_name + ".json", mode='wb')
    f1.write(r)
    f1.close()
    print("http")
    return ele_json


kulist = get_json("http://mediapi.medicool.cn/mediexam/Swcf/Calculator.svc/kulist", 'kulist')
for a in kulist['kutypelist']:
    # os.makedirs("out/" + a['name'])
    print(a['name'])
    for b in a['kutypelist']:
        print("     "+b['name'])
        # os.makedirs("out/" + a['name'] + "/" + b['name'])
        if not b['Id'] == '12':
            continue
        for c in b['kulist']:
            print("         " + c['name'])
            file_path = "out/" + a['name'] + "/" + b['name'] + "/" + c['name'] + '.db'
            conn = sqlite3.connect(file_path)
            with open("schema.sql", mode='r', encoding='utf-8') as f:
                conn.cursor().executescript(f.read())
                f.close()
                conn.commit()
            conn.execute("PRAGMA user_version=1")
            bid = c['BId']
            id = c['Id']
            print(id)
            tiku = get_json("http://mediapi.medicool.cn/mediexam/Swcf/Calculator.svc/typezjlist?kuId=" + id,
                            'typezjlist.kuId' + id)
            chap = []
            for d in tiku['typelist']:
                chapter = d['name']
                print("             " + chapter+"("+d['zongnum']+")")
                try:
                    chap_id = chap.index(chapter)
                except ValueError:
                    chap.append(chapter)
                    chap_id = chap.index(chapter)
                tiku_id = d['Id']
                start = '0'
                has = True
                while has:
                    print("获取题目页数据")
                    timu = get_json("http://mediapi.medicool.cn/mediexam/Swcf/Calculator.svc/zjexamlist?CId="+id +
                                    "&EIdlist="+start+"%2C0%2C0&TId="+tiku_id, 'zjexamlist.' + id + "." + start + "." + tiku_id)
                    has = True if timu['ExamAlist'] else False
                    for e in timu['ExamAlist']:
                        questionText = e['questionText']
                        # print(questionText)
                        start = e['questionId']
                        analysis = e['analysis']
                        # print(analysis)
                        answers = e['answers']
                        ans = ''
                        for choise in answers[0].split(" "):
                            ans = ans + chr(64+int(choise))
                        options = json.dumps(answers[1:], ensure_ascii=False)
                        # print(ans)
                        # print(options)
                        conn.cursor().execute(
                            "INSERT INTO " + ("DX" if len(answers[0]) == 1 else "DD") + "(Topic,OptionList,Result,"
                                                                                 "Explain,chapId)VALUES(?, ?, ?, ?, "
                                                                                 "?);",
                            (base64.b64encode(questionText.encode()).decode(), base64.b64encode(options.encode()).decode(), ans, base64.b64encode(analysis.encode()).decode(), chap_id))
                        conn.commit()
            for idx, val in enumerate(chap):
                conn.cursor().execute("INSERT INTO Chapter(chapId,name)VALUES(?, ?);",
                                          (idx, val))
                conn.commit()
            conn.close()
print("over")
