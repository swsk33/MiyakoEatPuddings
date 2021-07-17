package link.swsk33web.miyakogame.dao;

import link.swsk33web.miyakogame.dataobject.Player;
import link.swsk33web.miyakogame.model.RankInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PlayerDAO {

	/**
	 * 新增用户
	 */
	int add(Player player);

	/**
	 * 删除用户
	 */
	int delete(String userName);

	/**
	 * 修改用户
	 **/
	int update(Player player);

	/**
	 * 根据用户名查找用户
	 */
	Player findByUserName(String userName);

	/**
	 * 查询最高分前十用户
	 */
	List<RankInfo> findByHighScoreInTen();

	/**
	 * 通过用户名查询用户排名
	 */
	RankInfo findUserRankByUsername(String userName);

}