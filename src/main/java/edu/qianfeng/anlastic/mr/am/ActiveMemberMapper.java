package edu.qianfeng.anlastic.mr.am;

import edu.qianfeng.anlastic.model.dim.StatsCommonDimension;
import edu.qianfeng.anlastic.model.dim.StatsUserDimension;
import edu.qianfeng.anlastic.model.dim.base.BrowserDimension;
import edu.qianfeng.anlastic.model.dim.base.DateDimension;
import edu.qianfeng.anlastic.model.dim.base.KpiDimension;
import edu.qianfeng.anlastic.model.dim.base.PlatformDimension;
import edu.qianfeng.anlastic.model.value.TimeOutputValue;
import edu.qianfeng.common.DateEnum;
import edu.qianfeng.common.EventLogConstant;
import edu.qianfeng.common.KpiType;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * Created by lyd on 2018/6/5.
 * 活跃会员的mapper类
 */
public class ActiveMemberMapper extends TableMapper<StatsUserDimension, TimeOutputValue> {
    private static final Logger logger = Logger.getLogger(ActiveMemberMapper.class);
    private StatsUserDimension k = new StatsUserDimension();
    private TimeOutputValue v = new TimeOutputValue();
    private byte[] family = Bytes.toBytes(EventLogConstant.LOG_FAMILY_NAME);
    //kpi维度类
    private KpiDimension activeMemberKpi = new KpiDimension(KpiType.ACTIVE_MEMBER.kpiName);
    private KpiDimension browserActiveMemberKpi = new KpiDimension(KpiType.BROWSER_ACTIVE_MEMBER.kpiName);

    @Override
    protected void map(ImmutableBytesWritable key, Result value, Context context) throws IOException, InterruptedException {
        //重hbase的result中获取统计该指标所需要的字段
        String serverTime = Bytes.toString(value.getValue(family, Bytes.toBytes(EventLogConstant.LOG_COLUMN_NAME_SERVER_TIME)));
        String memberId = Bytes.toString(value.getValue(family, Bytes.toBytes(EventLogConstant.LOG_COLUMN_NAME_MEMBER_ID)));
        String plaform = Bytes.toString(value.getValue(family, Bytes.toBytes(EventLogConstant.LOG_COLUMN_NAME_PLATFORM_NAME)));
        String browserName = Bytes.toString(value.getValue(family, Bytes.toBytes(EventLogConstant.LOG_COLUMN_NAME_BROWSER_NAME)));
        String browserVersion = Bytes.toString(value.getValue(family, Bytes.toBytes(EventLogConstant.LOG_COLUMN_NAME_BROWSER_VERSION)));

        //判断该指标的必须字段不能为空
        if (StringUtils.isBlank(serverTime) || StringUtils.isBlank(memberId) || StringUtils.isBlank(plaform)) {
            logger.warn("serverTime&&memberId&&plaform三者任意一个都不能为空:serverTime:" + serverTime + " memberId:" + memberId + " platform:" + plaform);
            return;
        }
        //代码到这儿正常处理
        long longOfTime = Long.valueOf(serverTime);
        //设置map阶段输出的value的值
        this.v.setId(memberId);
        this.v.setTime(longOfTime);

        //时间维度
        DateDimension dateDimension = DateDimension.buildDate(longOfTime, DateEnum.DAY); //按天统计
        List<PlatformDimension> platformDimensions = PlatformDimension.buildList(plaform);
        List<BrowserDimension> browserDimensions = BrowserDimension.buildList(browserName, browserVersion);

        //获取statsCommondimension
        StatsCommonDimension statsCommonDimension = k.getStatsCommon();
        statsCommonDimension.setDateDimension(dateDimension);

        //默认一个browserDimension
        BrowserDimension defaultBrowserDimension = new BrowserDimension("", "");
        //输出
        for (PlatformDimension pl : platformDimensions) {
            //设置pl
            statsCommonDimension.setPlatformDimension(pl);
            statsCommonDimension.setKpiDimension(activeMemberKpi);
            this.k.setBrowser(defaultBrowserDimension);
            this.k.setStatsCommon(statsCommonDimension);
            context.write(this.k, this.v);

            //统计浏览器模块新增用户指标
            for (BrowserDimension browser : browserDimensions) {
                //覆盖kpi维度
                statsCommonDimension.setKpiDimension(browserActiveMemberKpi);
                this.k.setBrowser(browser);
                this.k.setStatsCommon(statsCommonDimension);
                context.write(this.k, this.v);
            }
        }
    }
}