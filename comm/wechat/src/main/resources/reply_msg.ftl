<xml>
    <ToUserName><![CDATA[${toUser}]]></ToUserName>
    <FromUserName><![CDATA[${fromUser}]]></FromUserName>
    <CreateTime>${timeStamp}</CreateTime>
    <#if ((MSG_TYPE) == "TEXT")>
    <MsgType><![CDATA[text]]></MsgType>
    <Content><![CDATA[${content}]]></Content>
    </#if>
    <#if ((MSG_TYPE) == "IMAGE")>
    <MsgType><![CDATA[image]]></MsgType>
    <Image>
        <MediaId><![CDATA[${mediaId}]]></MediaId>
    </Image>
    </#if>
    <#if ((MSG_TYPE) == "VOICE")>
    <MsgType><![CDATA[voice]]></MsgType>
    <Voice>
        <MediaId><![CDATA[${mediaId}]]></MediaId>
    </Voice>
    </#if>
    <#if ((MSG_TYPE) == "VIDEO")>
    <MsgType><![CDATA[video]]></MsgType>
    <Video>
        <MediaId><![CDATA[${mediaId}]]></MediaId>
        <Title><![CDATA[${title}]]></Title>
        <Description><![CDATA[${description}]]></Description>
    </Video>
    </#if>
    <#if ((MSG_TYPE) == "MUSIC")>
    <MsgType><![CDATA[music]]></MsgType>
    <Music>
        <Title><![CDATA[${title}]]></Title>
        <Description><![CDATA[${description}]]></Description>
        <MusicUrl><![CDATA[${musicUrl}]]></MusicUrl>
        <HQMusicUrl><![CDATA[${hqMusicUrl}]]></HQMusicUrl>
        <ThumbMediaId><![CDATA[${media_id}]]></ThumbMediaId>
    </Music>
    </#if>
    <#if ((MSG_TYPE) == "NEWS")>
    <MsgType><![CDATA[news]]></MsgType>
    <ArticleCount>${articleList$size}</ArticleCount>
    <#list articleList as article>
    <Articles>
        <item>
            <Title><![CDATA[${article.title}]]></Title>
            <Description><![CDATA[${article.description}]]></Description>
            <PicUrl><![CDATA[${article.picurl}]]></PicUrl>
            <Url><![CDATA[${article.url}]]></Url>
        </item>
    </Articles>
    </#list>
    </#if>
</xml>

