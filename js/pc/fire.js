//火控系统
//开火函数
function fire() {
	//在装填完毕的时候可以开火
	if (!isLoadingBullet) {
		//射击后装载时间
		isLoadingBullet = true;
		let loading = setTimeout(function() {
			isLoadingBullet = false;
		}, loadingInterval);
		//生成子弹
		let bullet = document.createElement('div');
		bullet.className = 'bullet';
		bullet.style.position = 'absolute';
		bullet.style.width = '15px';
		bullet.style.height = '24px';
		bullet.style.left = miyako.offsetLeft + miyako.offsetWidth + 'px';
		bullet.style.top = miyako.offsetTop + (0.3 * miyako.offsetHeight) + 'px';
		bullet.style.background = 'url(./img/bullet.png) no-repeat center/cover';
		bg.appendChild(bullet);
		//子弹飞行
		let bulletX = bullet.offsetLeft;
		let flyController = setInterval(function() {
			bulletX = bulletX + 2;
			bullet.style.left = bulletX + 'px';
			//如果子弹和敌人相碰或者飞出边界，则停止飞行并销毁自身
			if (collideCheck(bullet, puddingArray) || bullet.offsetLeft + bullet.offsetWidth > bg.offsetWidth) {
				clearInterval(flyController);
				bullet.remove();
			}
		}, 3);
	}
}

//碰撞检测与计算，传入子弹dom和构造敌人对象数组
function collideCheck(bullet, objectArray) {
	let isCollide = false;
	//依次判断这个子弹是否和每一个敌人相碰，相碰后把相应的敌人构造对象标记为已被击中，加分并跳出遍历
	for (let i = 0; i < objectArray.length; i++) {
		//条件1：子弹的右下部分在敌人贴图范围内
		let criteria1 = (bullet.offsetTop + bullet.offsetHeight) >= getPuddingPosition(objectArray[i]).top && (bullet.offsetLeft + bullet.offsetWidth) >= getPuddingPosition(objectArray[i]).left;
		//条件2：子弹左上部分在敌人贴图范围内
		let criteria2 = bullet.offsetTop <= (getPuddingPosition(objectArray[i]).top + objectArray[i].dom.offsetHeight) && bullet.offsetLeft <= (getPuddingPosition(objectArray[i]).left + objectArray[i].dom.offsetWidth);
		//总条件：条件1和条件2需要同时满足
		let criteriaTotal = criteria1 && criteria2;
		if (criteriaTotal) {
			isCollide = true;
			objectArray[i].dom.style.display = 'none';
			objectArray[i].isEaten = true;
			addScore(objectArray[i].score);
			break;
		}
	}
	return isCollide;
}
