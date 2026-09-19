package com.spt.learningmanage.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 分页插件的显式回归断言。
 *
 * <p>MyBatis-Plus 自 3.5.9 起把 JSqlParser 相关能力拆成独立构件
 * （{@code mybatis-plus-jsqlparser}），主构件不再传递 JSqlParser。
 * 若只升版本号而漏掉该构件，启动不会报错，只有首次执行分页查询时才会抛
 * {@code NoClassDefFoundError}——这类问题如果只靠深层链路的偶然覆盖，
 * 会一直潜伏到线上。这里把两件事钉死：
 * <ol>
 *   <li>{@link PaginationInnerInterceptor} 仍以 MYSQL 方言注册；</li>
 *   <li>分页解析所依赖的 JSqlParser 确实在 classpath 上且可用。</li>
 * </ol>
 */
class MybatisPaginationPluginContractTest {

    @Test
    void paginationInterceptorShouldBeRegisteredWithMySqlDialect() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        List<InnerInterceptor> inners = interceptor.getInterceptors();
        Assertions.assertEquals(1, inners.size(), "核心拦截器链的内容发生了变化");
        InnerInterceptor inner = inners.get(0);
        Assertions.assertInstanceOf(PaginationInnerInterceptor.class, inner);
        Assertions.assertEquals(DbType.MYSQL, ((PaginationInnerInterceptor) inner).getDbType());
    }

    @Test
    void jsqlparserShouldRemainAvailableForPaginationParsing() throws Exception {
        // 这个类来自 mybatis-plus-jsqlparser 拆出的构件，缺失即意味着分页能力不可用。
        Assertions.assertNotNull(
                Class.forName("net.sf.jsqlparser.parser.CCJSqlParserUtil"),
                "JSqlParser 不在 classpath 上，分页插件会在运行期失败");

        Statement statement = CCJSqlParserUtil.parse(
                "SELECT id FROM lm_task WHERE is_delete = 0 LIMIT 10 OFFSET 20");

        // 只断言「解析出了一条 SELECT」，不绑定具体实现类：
        // 简单 SELECT 解析为 PlainSelect，带 UNION 的会解析为 SetOperationList，
        // 两者都是 Select 的子类型，钉死具体类反而会制造假失败。
        Assertions.assertInstanceOf(Select.class, statement);
    }
}
