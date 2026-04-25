export const HOT_CITY_OPTIONS = [
  {
    name: '南京',
    regions: ['鼓楼', '玄武', '建邺', '秦淮', '江宁', '浦口']
  },
  {
    name: '苏州',
    regions: ['姑苏', '工业园区', '高新区', '吴中', '相城', '吴江']
  },
  {
    name: '杭州',
    regions: ['西湖', '拱墅', '上城', '滨江', '余杭', '萧山']
  },
  {
    name: '上海',
    regions: ['黄浦', '徐汇', '静安', '长宁', '浦东', '杨浦', '闵行']
  },
  {
    name: '北京',
    regions: ['朝阳', '海淀', '东城', '西城', '丰台', '通州']
  },
  {
    name: '广州',
    regions: ['天河', '海珠', '越秀', '白云', '番禺', '黄埔']
  },
  {
    name: '深圳',
    regions: ['南山', '福田', '罗湖', '宝安', '龙岗', '龙华']
  },
  {
    name: '成都',
    regions: ['高新', '锦江', '武侯', '青羊', '成华', '双流']
  },
  {
    name: '武汉',
    regions: ['武昌', '洪山', '江汉', '硚口', '汉阳', '东湖高新']
  }
]

export const DEFAULT_CITY = HOT_CITY_OPTIONS[0].name

export function getRegionsByCity(city) {
  return HOT_CITY_OPTIONS.find((item) => item.name === city)?.regions || []
}
