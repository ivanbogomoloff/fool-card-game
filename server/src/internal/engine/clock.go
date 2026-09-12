package engine

import "time"

func unixMillisNow() int64 {
	return time.Now().UnixMilli()
}
